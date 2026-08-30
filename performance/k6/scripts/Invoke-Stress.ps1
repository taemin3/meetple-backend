[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet(25, 50, 75, 100, 200, 300, 400)]
    [int] $TargetRps,
    [string] $DatasetId = 'meetple-k6-baseline-v1',
    [string] $BaseUrl = 'http://127.0.0.1:8080',
    [switch] $AllowRemote,
    [string] $ConfirmTarget,
    [switch] $AcknowledgeStress,
    [string] $AwsProfile = 'meetple-deploy',
    [string] $AwsRegion = 'ap-northeast-2'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')

if (-not $AcknowledgeStress) {
    throw 'Stress execution is blocked. Pass -AcknowledgeStress only after the exact RPS stage is approved.'
}
if ($DatasetId -notmatch '^[a-zA-Z0-9-]{3,40}$') {
    throw 'DatasetId must contain 3-40 letters, numbers, or hyphens.'
}

$k6Root = Split-Path -Parent $PSScriptRoot
$scenario = Join-Path $k6Root 'scenarios\stress.js'
$datasetManifestPath = Join-Path $k6Root "data\manifests\$DatasetId.json"
if (-not (Test-Path -LiteralPath $datasetManifestPath)) {
    throw "Dataset manifest not found: $datasetManifestPath"
}
$datasetManifest = Get-Content -Raw -LiteralPath $datasetManifestPath | ConvertFrom-Json
$datasetMeetingCount = @($datasetManifest.meetings | Where-Object { $null -eq $_.deletedAtUtc }).Count
if ($datasetMeetingCount -lt 1000) {
    throw "Stress requires at least 1000 active dataset meetings; found $datasetMeetingCount."
}

$target = Assert-K6Environment -BaseUrl $BaseUrl -AllowRemote $AllowRemote.IsPresent `
    -ConfirmTarget $ConfirmTarget -RequireMeetingId $false
if ([string]$datasetManifest.baseUrl -cne $target.BaseUrl) {
    throw "Dataset target '$($datasetManifest.baseUrl)' does not match '$($target.BaseUrl)'."
}

$k6 = Get-K6Executable
$run = New-K6ResultDirectory -K6Root $k6Root -TestType "stress-$TargetRps-rps"
$summaryPath = Join-Path $run.Path 'k6-summary.json'
$metadataPath = Join-Path $run.Path 'run-metadata.json'
$startedAt = [DateTime]::UtcNow
$estimatedReadRequests = $TargetRps * 270

$previousBaseUrl = $env:K6_BASE_URL
$previousAllowRemote = $env:K6_ALLOW_REMOTE
$previousConfirmTarget = $env:K6_CONFIRM_TARGET
$previousTargetRps = $env:K6_TARGET_RPS
$previousDatasetManifest = $env:K6_DATASET_MANIFEST

try {
    $env:K6_BASE_URL = $target.BaseUrl
    $env:K6_ALLOW_REMOTE = if ($target.IsLocal) { 'false' } else { 'true' }
    $env:K6_CONFIRM_TARGET = if ($target.IsLocal) { '' } else { $target.Host }
    $env:K6_TARGET_RPS = [string] $TargetRps
    $env:K6_DATASET_MANIFEST = (Resolve-Path -LiteralPath $datasetManifestPath).Path.Replace('\', '/')

    $metadata = [ordered]@{
        runId = $run.RunId
        testType = 'stress'
        baseUrl = $target.BaseUrl
        targetHost = $target.Host
        targetRps = $TargetRps
        duration = '5m (30s ramp-up, 4m hold, 30s ramp-down)'
        requestDistribution = 'categories/list/detail/member profile: 25% each'
        estimatedReadRequests = $estimatedReadRequests
        setupLoginRequests = 1
        datasetId = $DatasetId
        datasetMeetingCount = $datasetMeetingCount
        startedAtUtc = $startedAt.ToString('o')
        postgresBusinessDataMutation = 'none'
        redisMutation = 'one login session creation'
    }
    $metadata | ConvertTo-Json | Set-Content -LiteralPath $metadataPath -Encoding utf8

    Write-Host "Planned stress: target $TargetRps RPS, approximately $estimatedReadRequests read requests plus one setup login."
    Write-Host 'Distribution: categories 25%, meeting list 25%, meeting detail 25%, member profile 25%.'
    $arguments = @('run', '--summary-export', $summaryPath, '--tag', "run_id=$($run.RunId)", $scenario)
    $exitCode = Invoke-K6WithGuard -K6Executable $k6 -K6Arguments $arguments `
        -EnableStagingGuard (-not $target.IsLocal) -AwsProfile $AwsProfile -AwsRegion $AwsRegion

    $requestCount = $null
    $summaryError = $null
    try {
        $requestCount = Assert-K6Summary -SummaryPath $summaryPath -MinimumHttpRequests 100
    } catch {
        $summaryError = $_
    }

    $metadata['exitCode'] = $exitCode
    if ($null -ne $requestCount) {
        $metadata['actualRequests'] = $requestCount
    }
    $metadata['finishedAtUtc'] = [DateTime]::UtcNow.ToString('o')
    $metadata | ConvertTo-Json | Set-Content -LiteralPath $metadataPath -Encoding utf8

    Write-Host "Stress result: $($run.Path)"
    Write-Host 'Do not start the next RPS stage until k6, CloudWatch, ECS tasks, WAL, and Slack are healthy.'
    if ($null -ne $summaryError) {
        throw $summaryError
    }
    if ($exitCode -ne 0) {
        throw "k6 Stress failed with exit code $exitCode."
    }
} finally {
    $env:K6_BASE_URL = $previousBaseUrl
    $env:K6_ALLOW_REMOTE = $previousAllowRemote
    $env:K6_CONFIRM_TARGET = $previousConfirmTarget
    $env:K6_TARGET_RPS = $previousTargetRps
    $env:K6_DATASET_MANIFEST = $previousDatasetManifest
}
