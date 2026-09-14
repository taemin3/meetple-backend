[CmdletBinding()]
param(
    [string] $OutputPath = '.\performance\results\chat-staging\chat-staging-locks.jsonl',
    [ValidateRange(0.05, 5.0)]
    [double] $IntervalSeconds = 0.1,
    [ValidateRange(1024, 65535)]
    [int] $LocalPort = 15433,
    [string] $AwsProfile = 'meetple-deploy',
    [string] $AwsRegion = 'ap-northeast-2',
    [switch] $Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'k6\scripts\Common.ps1')
. (Join-Path $PSScriptRoot 'k6\scripts\PostgresQps.Common.ps1')

$connection = Get-StagingPostgresConnection `
    -LocalPort $LocalPort `
    -AwsProfile $AwsProfile `
    -AwsRegion $AwsRegion
$psql = Get-PsqlExecutable
$outputDirectory = Split-Path -Parent $OutputPath
if (-not [string]::IsNullOrWhiteSpace($outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}
if (Test-Path -LiteralPath $OutputPath) {
    throw "Output already exists: $OutputPath"
}

$sql = @"
\pset tuples_only on
\pset format unaligned
select coalesce(json_agg(row_to_json(sample)), '[]'::json)
from (
    select
        clock_timestamp() as observed_at,
        activity.pid,
        activity.state,
        activity.wait_event_type,
        activity.wait_event,
        pg_blocking_pids(activity.pid) as blocking_pids,
        extract(milliseconds from clock_timestamp() - activity.query_start)::bigint
            as query_age_ms,
        left(regexp_replace(activity.query, '[[:space:]]+', ' ', 'g'), 240) as query
    from pg_stat_activity activity
    where activity.datname = current_database()
      and activity.pid <> pg_backend_pid()
      and (
          activity.wait_event_type = 'Lock'
          or cardinality(pg_blocking_pids(activity.pid)) > 0
      )
    order by activity.pid
) sample;
\watch $IntervalSeconds
"@
$sqlPath = Join-Path ([IO.Path]::GetTempPath()) ("meetple-chat-locks-{0}.sql" -f [Guid]::NewGuid().ToString('N'))
$previousPassword = $env:PGPASSWORD
$previousSslMode = $env:PGSSLMODE
$previousConnectTimeout = $env:PGCONNECT_TIMEOUT
try {
    [IO.File]::WriteAllText($sqlPath, $sql, [Text.UTF8Encoding]::new($false))
    $env:PGPASSWORD = $connection.Password
    $env:PGSSLMODE = 'require'
    $env:PGCONNECT_TIMEOUT = '5'
    Write-Host "Staging PostgreSQL lock sampling: every $IntervalSeconds second(s)"
    Write-Host "Output: $OutputPath"
    Write-Host 'Stop with Ctrl+C immediately after the load condition finishes.'

    $arguments = @(
        '--host', $connection.HostName,
        '--port', [string] $connection.Port,
        '--username', $connection.Username,
        '--dbname', $connection.Database,
        '--no-password', '--no-psqlrc', '--quiet',
        '--set', 'ON_ERROR_STOP=1',
        '--file', $sqlPath
    )
    $sampler = {
        & $psql @arguments |
            Where-Object { $_ -match '^\s*\[' } |
            Tee-Object -FilePath $OutputPath
    }
    if ($Quiet) {
        & $sampler | Out-Null
    } else {
        & $sampler
    }
} finally {
    $env:PGPASSWORD = $previousPassword
    $env:PGSSLMODE = $previousSslMode
    $env:PGCONNECT_TIMEOUT = $previousConnectTimeout
    Remove-Item -LiteralPath $sqlPath -Force -ErrorAction SilentlyContinue
}
