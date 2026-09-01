[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('categories', 'meeting-list', 'meeting-list-summary', 'meeting-detail', 'member-me')]
    [string] $Endpoint,
    [Parameter(Mandatory = $true)]
    [ValidateSet(200, 300, 400)]
    [int] $TargetRps,
    [string] $DatasetId = 'meetple-k6-baseline-v1',
    [string] $BaseUrl = 'http://127.0.0.1:8080',
    [switch] $AllowRemote,
    [string] $ConfirmTarget,
    [switch] $AcknowledgeIsolated,
    [switch] $CaptureDbQps,
    [ValidateRange(1024, 65535)]
    [int] $DbLocalPort = 15433,
    [string] $AwsProfile = 'meetple-deploy',
    [string] $AwsRegion = 'ap-northeast-2'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')
. (Join-Path $PSScriptRoot 'PostgresQps.Common.ps1')

if (-not $AcknowledgeIsolated) {
    throw 'Isolated API execution is blocked. Pass -AcknowledgeIsolated only after the exact endpoint and RPS are approved.'
}
if ($DatasetId -notmatch '^[a-zA-Z0-9-]{3,40}$') {
    throw 'DatasetId must contain 3-40 letters, numbers, or hyphens.'
}

$k6Root = Split-Path -Parent $PSScriptRoot
$scenario = Join-Path $k6Root 'scenarios\isolated.js'
$datasetManifestPath = Join-Path $k6Root "data\manifests\$DatasetId.json"
if (-not (Test-Path -LiteralPath $datasetManifestPath)) {
    throw "Dataset manifest not found: $datasetManifestPath"
}
$datasetManifest = Get-Content -Raw -LiteralPath $datasetManifestPath | ConvertFrom-Json
$datasetMeetingCount = @($datasetManifest.meetings | Where-Object { $null -eq $_.deletedAtUtc }).Count
if ($datasetMeetingCount -lt 1000) {
    throw "Isolated API testing requires at least 1000 active dataset meetings; found $datasetMeetingCount."
}

$target = Assert-K6Environment -BaseUrl $BaseUrl -AllowRemote $AllowRemote.IsPresent `
    -ConfirmTarget $ConfirmTarget -RequireMeetingId $false
if ([string] $datasetManifest.baseUrl -cne $target.BaseUrl) {
    throw "Dataset target '$($datasetManifest.baseUrl)' does not match '$($target.BaseUrl)'."
}
if ($CaptureDbQps -and $target.IsLocal) {
    throw 'CaptureDbQps currently supports remote staging through the approved SSM tunnel only.'
}

$k6 = Get-K6Executable
$run = New-K6ResultDirectory -K6Root $k6Root -TestType "isolated-$Endpoint-$TargetRps-rps"
$summaryPath = Join-Path $run.Path 'k6-summary.json'
$metadataPath = Join-Path $run.Path 'run-metadata.json'
$qpsBeforePath = Join-Path $run.Path 'db-qps-before.json'
$qpsAfterPath = Join-Path $run.Path 'db-qps-after.json'
$qpsReportPath = Join-Path $run.Path 'db-qps.json'
$startedAt = [DateTime]::UtcNow
$estimatedReadRequests = $TargetRps * 150

$previousBaseUrl = $env:K6_BASE_URL
$previousAllowRemote = $env:K6_ALLOW_REMOTE
$previousConfirmTarget = $env:K6_CONFIRM_TARGET
$previousTargetRps = $env:K6_TARGET_RPS
$previousDatasetManifest = $env:K6_DATASET_MANIFEST
$previousIsolatedEndpoint = $env:K6_ISOLATED_ENDPOINT
$dbConnection = $null

try {
    $env:K6_BASE_URL = $target.BaseUrl
    $env:K6_ALLOW_REMOTE = if ($target.IsLocal) { 'false' } else { 'true' }
    $env:K6_CONFIRM_TARGET = if ($target.IsLocal) { '' } else { $target.Host }
    $env:K6_TARGET_RPS = [string] $TargetRps
    $env:K6_ISOLATED_ENDPOINT = $Endpoint
    $env:K6_DATASET_MANIFEST = (Resolve-Path -LiteralPath $datasetManifestPath).Path.Replace('\', '/')

    $metadata = [ordered]@{
        runId = $run.RunId
        testType = 'isolated'
        endpoint = $Endpoint
        baseUrl = $target.BaseUrl
        targetHost = $target.Host
        targetRps = $TargetRps
        duration = '3m (30s ramp-up, 2m hold, 30s ramp-down)'
        requestDistribution = "$Endpoint 100%"
        estimatedReadRequests = $estimatedReadRequests
        setupLoginRequests = 1
        datasetId = $DatasetId
        datasetMeetingCount = $datasetMeetingCount
        startedAtUtc = $startedAt.ToString('o')
        postgresBusinessDataMutation = 'none'
        redisMutation = 'one login session creation'
        databaseQpsCapture = $CaptureDbQps.IsPresent
    }
    $metadata | ConvertTo-Json | Set-Content -LiteralPath $metadataPath -Encoding utf8

    Write-Host "Planned isolated test: $Endpoint at $TargetRps RPS, approximately $estimatedReadRequests read requests plus one setup login."
    Write-Host 'Duration: 30s ramp-up, 2m hold, 30s ramp-down.'
    Write-Host 'External effects: one Redis login session; no PostgreSQL business mutation, email, FCM, Outbox, or Kafka event.'

    $qpsBefore = $null
    if ($CaptureDbQps) {
        Write-Host "PostgreSQL QPS capture: read-only pg_stat_statements snapshots through 127.0.0.1:$DbLocalPort."
        $dbConnection = Get-StagingPostgresConnection -LocalPort $DbLocalPort `
            -AwsProfile $AwsProfile -AwsRegion $AwsRegion
        $qpsBefore = Get-PostgresStatementSnapshot -Connection $dbConnection
        $qpsBefore | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $qpsBeforePath -Encoding utf8
    }

    $exitCode = $null
    $k6Error = $null
    try {
        $arguments = @('run', '--summary-export', $summaryPath, '--tag', "run_id=$($run.RunId)", $scenario)
        $exitCode = Invoke-K6WithGuard -K6Executable $k6 -K6Arguments $arguments `
            -EnableStagingGuard (-not $target.IsLocal) -AwsProfile $AwsProfile -AwsRegion $AwsRegion
    } catch {
        $k6Error = $_
    }

    $requestCount = $null
    $summaryError = $null
    try {
        $requestCount = Assert-K6Summary -SummaryPath $summaryPath -MinimumHttpRequests 100
    } catch {
        $summaryError = $_
    }

    $qpsError = $null
    if ($CaptureDbQps -and $null -ne $qpsBefore) {
        try {
            $qpsAfter = Get-PostgresStatementSnapshot -Connection $dbConnection
            $qpsAfter | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $qpsAfterPath -Encoding utf8
            $qpsReport = Compare-PostgresStatementSnapshots -Before $qpsBefore -After $qpsAfter `
                -HttpRequestCount $(if ($null -eq $requestCount) { 0 } else { $requestCount }) `
                -TargetApiRps $TargetRps
            $qpsReport | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $qpsReportPath -Encoding utf8
            Write-Host ("Observed profile-average database QPS: {0:N2}; statements per HTTP request: {1:N2}; projected DB QPS at {2} API RPS: {3:N2}." -f `
                $qpsReport.observedDatabaseQps, $qpsReport.statementsPerHttpRequest, $TargetRps, `
                $qpsReport.projectedDatabaseQpsAtTargetApiRps)
        } catch {
            $qpsError = $_
        }
    }

    if ($null -ne $exitCode) {
        $metadata['exitCode'] = $exitCode
    }
    if ($null -ne $requestCount) {
        $metadata['actualRequests'] = $requestCount
    }
    $metadata['finishedAtUtc'] = [DateTime]::UtcNow.ToString('o')
    $metadata | ConvertTo-Json | Set-Content -LiteralPath $metadataPath -Encoding utf8

    Write-Host "Isolated result: $($run.Path)"
    Write-Host 'Do not start another endpoint until k6, CloudWatch, ECS tasks, RDS, WAL, and Slack are healthy.'
    if ($null -ne $k6Error) {
        throw $k6Error
    }
    if ($null -ne $summaryError) {
        throw $summaryError
    }
    if ($null -ne $qpsError) {
        throw $qpsError
    }
    if ($exitCode -ne 0) {
        throw "k6 isolated API test failed with exit code $exitCode."
    }
} finally {
    if ($null -ne $dbConnection) {
        $dbConnection.Password = $null
    }
    $env:K6_BASE_URL = $previousBaseUrl
    $env:K6_ALLOW_REMOTE = $previousAllowRemote
    $env:K6_CONFIRM_TARGET = $previousConfirmTarget
    $env:K6_TARGET_RPS = $previousTargetRps
    $env:K6_DATASET_MANIFEST = $previousDatasetManifest
    $env:K6_ISOLATED_ENDPOINT = $previousIsolatedEndpoint
}
