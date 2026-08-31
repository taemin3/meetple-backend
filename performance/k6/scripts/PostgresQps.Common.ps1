Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:StagingDbInstanceIdentifier = 'meetple-staging-postgres'

function Get-PsqlExecutable {
    $command = Get-Command psql -ErrorAction SilentlyContinue
    if ($null -ne $command) {
        return $command.Source
    }

    $candidates = @(
        Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\psql.exe' -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending |
            Select-Object -ExpandProperty FullName
    )
    if ($candidates.Count -gt 0) {
        return $candidates[0]
    }

    throw @'
psql is required for PostgreSQL QPS capture but was not found.
Install PostgreSQL command-line tools, open a new PowerShell window, and run: psql --version
The QPS collector never stores the database password in a file or command argument.
'@
}

function Test-TcpPort {
    param(
        [Parameter(Mandatory = $true)][string] $HostName,
        [Parameter(Mandatory = $true)][int] $Port,
        [int] $TimeoutMilliseconds = 2000
    )

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connectTask = $client.ConnectAsync($HostName, $Port)
        return $connectTask.Wait($TimeoutMilliseconds) -and $client.Connected
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Get-StagingPostgresConnection {
    param(
        [Parameter(Mandatory = $true)][int] $LocalPort,
        [string] $AwsProfile = 'meetple-deploy',
        [string] $AwsRegion = 'ap-northeast-2'
    )

    if (-not (Test-TcpPort -HostName '127.0.0.1' -Port $LocalPort)) {
        throw "No PostgreSQL tunnel is listening on 127.0.0.1:$LocalPort. Start Open-StagingPostgresTunnel.ps1 in another PowerShell window first."
    }

    $db = Invoke-AwsJson -Arguments @(
        'rds', 'describe-db-instances',
        '--db-instance-identifier', $script:StagingDbInstanceIdentifier,
        '--profile', $AwsProfile, '--region', $AwsRegion, '--output', 'json'
    )
    $instance = $db.DBInstances[0]
    $secretArn = [string] $instance.MasterUserSecret.SecretArn
    if ([string]::IsNullOrWhiteSpace($secretArn)) {
        throw 'RDS master user secret ARN was not returned.'
    }

    $secretValue = Invoke-AwsJson -Arguments @(
        'secretsmanager', 'get-secret-value', '--secret-id', $secretArn,
        '--profile', $AwsProfile, '--region', $AwsRegion, '--output', 'json'
    )
    $secret = $secretValue.SecretString | ConvertFrom-Json
    if ([string]::IsNullOrWhiteSpace([string] $secret.username) -or
        [string]::IsNullOrWhiteSpace([string] $secret.password)) {
        throw 'RDS secret does not contain username and password.'
    }

    return [pscustomobject]@{
        HostName = '127.0.0.1'
        Port = $LocalPort
        Database = [string] $instance.DBName
        Username = [string] $secret.username
        Password = [string] $secret.password
    }
}

function Invoke-PostgresScalar {
    param(
        [Parameter(Mandatory = $true)][pscustomobject] $Connection,
        [Parameter(Mandatory = $true)][string] $Sql
    )

    $psql = Get-PsqlExecutable
    $sqlPath = Join-Path ([System.IO.Path]::GetTempPath()) ("meetple-qps-{0}.sql" -f [Guid]::NewGuid().ToString('N'))
    $previousPassword = $env:PGPASSWORD
    $previousSslMode = $env:PGSSLMODE
    $previousConnectTimeout = $env:PGCONNECT_TIMEOUT

    try {
        [System.IO.File]::WriteAllText($sqlPath, $Sql, [System.Text.UTF8Encoding]::new($false))
        $env:PGPASSWORD = $Connection.Password
        $env:PGSSLMODE = 'require'
        $env:PGCONNECT_TIMEOUT = '5'

        $arguments = @(
            '--host', $Connection.HostName,
            '--port', [string] $Connection.Port,
            '--username', $Connection.Username,
            '--dbname', $Connection.Database,
            '--no-password', '--no-psqlrc', '--tuples-only', '--no-align', '--quiet',
            '--set', 'ON_ERROR_STOP=1',
            '--file', $sqlPath
        )
        $output = @(& $psql @arguments 2>&1)
        if ($LASTEXITCODE -ne 0) {
            throw "psql query failed: $($output -join [Environment]::NewLine)"
        }
        return ($output -join [Environment]::NewLine).Trim()
    } finally {
        $env:PGPASSWORD = $previousPassword
        $env:PGSSLMODE = $previousSslMode
        $env:PGCONNECT_TIMEOUT = $previousConnectTimeout
        Remove-Item -LiteralPath $sqlPath -Force -ErrorAction SilentlyContinue
    }
}

function Get-PostgresStatementSnapshot {
    param([Parameter(Mandatory = $true)][pscustomobject] $Connection)

    $extensionAvailable = Invoke-PostgresScalar -Connection $Connection -Sql @'
SELECT EXISTS (
    SELECT 1
    FROM pg_extension
    WHERE extname = 'pg_stat_statements'
);
'@
    if ($extensionAvailable -cne 't') {
        throw @'
pg_stat_statements is not installed in the staging database.
The collector will not create it automatically because that is a database change.
Create the extension only after separate approval, then rerun the read-only collector.
'@
    }

    $snapshotJson = Invoke-PostgresScalar -Connection $Connection -Sql @'
SELECT json_build_object(
    'capturedAtUtc', to_char(clock_timestamp() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
    'statsReset', (SELECT stats_reset FROM pg_stat_statements_info),
    'dealloc', (SELECT dealloc FROM pg_stat_statements_info),
    'statements', COALESCE(
        json_agg(
            json_build_object(
                'databaseId', dbid::text,
                'userId', userid::text,
                'queryId', queryid::text,
                'topLevel', toplevel,
                'calls', calls,
                'totalExecTimeMs', total_exec_time,
                'meanExecTimeMs', mean_exec_time,
                'rows', rows,
                'query', left(query, 1000)
            ) ORDER BY total_exec_time DESC
        ),
        '[]'::json
    )
)::text
FROM pg_stat_statements
WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
  AND query NOT ILIKE '%pg_stat_statements%'
  AND query NOT ILIKE '%FROM pg_extension%';
'@

    if ([string]::IsNullOrWhiteSpace($snapshotJson)) {
        throw 'pg_stat_statements returned an empty snapshot.'
    }
    return $snapshotJson | ConvertFrom-Json
}

function Compare-PostgresStatementSnapshots {
    param(
        [Parameter(Mandatory = $true)][pscustomobject] $Before,
        [Parameter(Mandatory = $true)][pscustomobject] $After,
        [int] $HttpRequestCount = 0,
        [int] $TargetApiRps = 0,
        [int] $TopStatementCount = 20
    )

    if ([string] $Before.statsReset -cne [string] $After.statsReset) {
        throw 'pg_stat_statements counters were reset during the test; QPS cannot be calculated.'
    }

    $beforeTime = [DateTimeOffset]::Parse([string] $Before.capturedAtUtc)
    $afterTime = [DateTimeOffset]::Parse([string] $After.capturedAtUtc)
    $durationSeconds = ($afterTime - $beforeTime).TotalSeconds
    if ($durationSeconds -le 0) {
        throw 'PostgreSQL snapshot duration must be positive.'
    }

    $beforeByKey = @{}
    foreach ($statement in @($Before.statements)) {
        $key = '{0}|{1}|{2}|{3}' -f $statement.databaseId, $statement.userId, $statement.queryId, $statement.topLevel
        $beforeByKey[$key] = $statement
    }

    $deltas = @(
        foreach ($statement in @($After.statements)) {
            $key = '{0}|{1}|{2}|{3}' -f $statement.databaseId, $statement.userId, $statement.queryId, $statement.topLevel
            $beforeStatement = $beforeByKey[$key]
            $beforeCalls = if ($null -eq $beforeStatement) { 0L } else { [long] $beforeStatement.calls }
            $beforeExecTime = if ($null -eq $beforeStatement) { 0.0 } else { [double] $beforeStatement.totalExecTimeMs }
            $deltaCalls = [long] $statement.calls - $beforeCalls
            $deltaExecTime = [double] $statement.totalExecTimeMs - $beforeExecTime
            if ($deltaCalls -gt 0 -and $deltaExecTime -ge 0) {
                [pscustomobject][ordered]@{
                    queryId = [string] $statement.queryId
                    calls = $deltaCalls
                    callsPerSecond = $deltaCalls / $durationSeconds
                    totalExecTimeMs = $deltaExecTime
                    meanExecTimeMs = $deltaExecTime / $deltaCalls
                    rows = [long] $statement.rows - $(if ($null -eq $beforeStatement) { 0L } else { [long] $beforeStatement.rows })
                    query = [string] $statement.query
                }
            }
        }
    )

    $totalCalls = [long] (($deltas | Measure-Object -Property calls -Sum).Sum)
    $totalExecTimeMs = [double] (($deltas | Measure-Object -Property totalExecTimeMs -Sum).Sum)
    $deallocDelta = [long] $After.dealloc - [long] $Before.dealloc
    $statementsPerHttpRequest = if ($HttpRequestCount -gt 0) { $totalCalls / $HttpRequestCount } else { $null }

    return [pscustomobject][ordered]@{
        capturedFromUtc = $Before.capturedAtUtc
        capturedToUtc = $After.capturedAtUtc
        durationSeconds = $durationSeconds
        observedStatementCalls = $totalCalls
        observedDatabaseQps = $totalCalls / $durationSeconds
        httpRequestCount = $HttpRequestCount
        statementsPerHttpRequest = $statementsPerHttpRequest
        targetApiRps = $TargetApiRps
        projectedDatabaseQpsAtTargetApiRps = if ($TargetApiRps -gt 0 -and $null -ne $statementsPerHttpRequest) {
            $statementsPerHttpRequest * $TargetApiRps
        } else {
            $null
        }
        totalDatabaseExecTimeMs = $totalExecTimeMs
        averageDatabaseExecTimeMs = if ($totalCalls -gt 0) { $totalExecTimeMs / $totalCalls } else { 0.0 }
        statementDeallocations = $deallocDelta
        limitations = @(
            'Includes every statement executed in the database during the snapshot interval, including low-volume background work.',
            'Normalized pg_stat_statements text is stored only under the gitignored performance results directory.',
            'Observed QPS covers the complete ramp/hold/ramp profile; projected target QPS multiplies statements per request by the requested API RPS.',
            'This is statement QPS, not RDS read/write IOPS and not API RPS.'
        )
        topStatementsByTotalExecTime = @(
            $deltas |
                Sort-Object totalExecTimeMs -Descending |
                Select-Object -First $TopStatementCount
        )
        topStatementsByCalls = @(
            $deltas |
                Sort-Object calls -Descending |
                Select-Object -First $TopStatementCount
        )
    }
}
