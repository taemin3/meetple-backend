[CmdletBinding()]
param(
    [string] $DatasetId = 'meetple-k6-baseline-v1',
    [string] $BaseUrl = 'http://127.0.0.1:8080',
    [switch] $AllowRemote,
    [string] $ConfirmTarget,
    [switch] $AcknowledgeJfrProfile,
    [string] $AwsProfile = 'meetple-deploy',
    [string] $AwsRegion = 'ap-northeast-2'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')

if (-not $AcknowledgeJfrProfile) {
    throw 'JFR profile load is blocked. Pass -AcknowledgeJfrProfile only after the exact 300-to-350 RPS run is approved.'
}
if ($DatasetId -notmatch '^[a-zA-Z0-9-]{3,40}$') {
    throw 'DatasetId must contain 3-40 letters, numbers, or hyphens.'
}

$k6Root = Split-Path -Parent $PSScriptRoot
$scenario = Join-Path $k6Root 'scenarios\jfr-profile.js'
$datasetManifestPath = Join-Path $k6Root "data\manifests\$DatasetId.json"
if (-not (Test-Path -LiteralPath $datasetManifestPath)) {
    throw "Dataset manifest not found: $datasetManifestPath"
}
$datasetManifest = Get-Content -Raw -LiteralPath $datasetManifestPath | ConvertFrom-Json
$datasetMeetingCount = @($datasetManifest.meetings | Where-Object { $null -eq $_.deletedAtUtc }).Count
if ($datasetMeetingCount -lt 1000) {
    throw "JFR profile requires at least 1000 active dataset meetings; found $datasetMeetingCount."
}

$target = Assert-K6Environment -BaseUrl $BaseUrl -AllowRemote $AllowRemote.IsPresent `
    -ConfirmTarget $ConfirmTarget -RequireMeetingId $false
if ([string]$datasetManifest.baseUrl -cne $target.BaseUrl) {
    throw "Dataset target '$($datasetManifest.baseUrl)' does not match '$($target.BaseUrl)'."
}

$k6 = Get-K6Executable
$run = New-K6ResultDirectory -K6Root $k6Root -TestType 'jfr-profile-300-350-rps'
$summaryPath = Join-Path $run.Path 'k6-summary.json'
$metadataPath = Join-Path $run.Path 'run-metadata.json'
$startedAt = [DateTime]::UtcNow
$estimatedReadRequests = 79500
$duration = '4m30s plus up to 30s graceful stop'

$previousBaseUrl = $env:K6_BASE_URL
$previousAllowRemote = $env:K6_ALLOW_REMOTE
$previousConfirmTarget = $env:K6_CONFIRM_TARGET
$previousDatasetManifest = $env:K6_DATASET_MANIFEST

try {
    $env:K6_BASE_URL = $target.BaseUrl
    $env:K6_ALLOW_REMOTE = if ($target.IsLocal) { 'false' } else { 'true' }
    $env:K6_CONFIRM_TARGET = if ($target.IsLocal) { '' } else { $target.Host }
    $env:K6_DATASET_MANIFEST = (Resolve-Path -LiteralPath $datasetManifestPath).Path.Replace('\', '/')

    $metadata = [ordered]@{
        runId = $run.RunId
        testType = 'jfr-profile'
        baseUrl = $target.BaseUrl
        targetHost = $target.Host
        stages = @(
            [ordered]@{ targetRps = 300; hold = '1m' }
            [ordered]@{ targetRps = 350; hold = '2m' }
        )
        duration = $duration
        preAllocatedVUs = 440
        maxVUs = 880
        requestDistribution = 'categories/list/detail/member profile: 25% each'
        estimatedReadRequests = $estimatedReadRequests
        setupLoginRequests = 1
        datasetId = $DatasetId
        datasetMeetingCount = $datasetMeetingCount
        startedAtUtc = $startedAt.ToString('o')
        jfrRecording = 'JFR must be started separately on the backend JVM before this script runs.'
        latencyAndDropsAreReportOnly = $true
        hardStops = 'HTTP/API/contract/auth/business failure; overall p99 >= 3000ms after the first 30s; ECS instability; task replacement; CloudWatch ALARM; ALB target 5xx'
        postgresBusinessDataMutation = 'none'
        redisMutation = 'one login session creation'
    }
    $metadata | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $metadataPath -Encoding utf8

    Write-Host 'Planned JFR profile: ramp to 300 RPS 30s -> 300 RPS 1m -> ramp to 350 RPS 30s -> 350 RPS 2m -> ramp down 30s.'
    Write-Host "Total duration: $duration; approximately $estimatedReadRequests read requests plus one setup login."
    Write-Host 'Distribution: categories 25%, meeting list 25%, meeting detail 25%, member profile 25%.'
    Write-Host 'Confirm that JFR recording is active on the backend JVM before continuing.'
    Write-Host 'Latency threshold crossings and dropped iterations are recorded without aborting the run.'
    Write-Host 'HTTP/API/contract/auth failures, overall p99 >= 3s after the first 30s, ECS instability, task replacement, CloudWatch ALARM, or ALB target 5xx still stop the run.'

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
    $metadata | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $metadataPath -Encoding utf8

    Write-Host "JFR profile load result: $($run.Path)"
    Write-Host 'Stop and download the JFR recording, then capture CloudWatch for the same UTC interval.'
    if ($null -ne $summaryError) {
        throw $summaryError
    }
    if ($exitCode -ne 0) {
        throw "k6 JFR profile completed or stopped with exit code $exitCode. Review thresholds and run duration in the saved result."
    }
} finally {
    $env:K6_BASE_URL = $previousBaseUrl
    $env:K6_ALLOW_REMOTE = $previousAllowRemote
    $env:K6_CONFIRM_TARGET = $previousConfirmTarget
    $env:K6_DATASET_MANIFEST = $previousDatasetManifest
}
