[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet(5, 10, 30)]
    [int] $Vus,
    [string] $BaseUrl = 'http://127.0.0.1:8080',
    [ValidateRange(1, 60)]
    [int] $IterationSeconds = 5,
    [string] $DatasetId,
    [switch] $AllowRemote,
    [string] $ConfirmTarget,
    [switch] $AcknowledgeLoad,
    [string] $AwsProfile = 'meetple-deploy',
    [string] $AwsRegion = 'ap-northeast-2'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')

if (-not $AcknowledgeLoad) {
    throw 'Load execution is blocked. Pass -AcknowledgeLoad only after Smoke and CloudWatch are healthy.'
}

$k6Root = Split-Path -Parent $PSScriptRoot
$scenario = Join-Path $k6Root 'scenarios\load.js'
$datasetManifestPath = $null
$datasetMeetingCount = 0
if (-not [string]::IsNullOrWhiteSpace($DatasetId)) {
    if ($DatasetId -notmatch '^[a-zA-Z0-9-]{3,40}$') {
        throw 'DatasetId must contain 3-40 letters, numbers, or hyphens.'
    }
    $datasetManifestPath = Join-Path $k6Root "data\manifests\$DatasetId.json"
    if (-not (Test-Path -LiteralPath $datasetManifestPath)) {
        throw "Dataset manifest not found: $datasetManifestPath"
    }
    $datasetManifest = Get-Content -Raw -LiteralPath $datasetManifestPath | ConvertFrom-Json
    $datasetMeetingCount = @($datasetManifest.meetings | Where-Object { $null -eq $_.deletedAtUtc }).Count
    if ($datasetMeetingCount -lt 1) {
        throw 'Dataset manifest does not contain an active meeting.'
    }
}
$target = Assert-K6Environment -BaseUrl $BaseUrl -AllowRemote $AllowRemote.IsPresent `
    -ConfirmTarget $ConfirmTarget -RequireMeetingId ([string]::IsNullOrWhiteSpace($DatasetId))
if ($null -ne $datasetManifestPath -and [string]$datasetManifest.baseUrl -cne $target.BaseUrl) {
    throw "Dataset target '$($datasetManifest.baseUrl)' does not match '$($target.BaseUrl)'."
}
$k6 = Get-K6Executable
$run = New-K6ResultDirectory -K6Root $k6Root -TestType "load-$Vus-vu"
$summaryPath = Join-Path $run.Path 'k6-summary.json'
$metadataPath = Join-Path $run.Path 'run-metadata.json'
$startedAt = [DateTime]::UtcNow
$estimatedIterations = [Math]::Floor((270 * $Vus) / $IterationSeconds)
$estimatedReadRequests = $estimatedIterations * 4

$previousBaseUrl = $env:K6_BASE_URL
$previousAllowRemote = $env:K6_ALLOW_REMOTE
$previousConfirmTarget = $env:K6_CONFIRM_TARGET
$previousLoadVus = $env:K6_LOAD_VUS
$previousIterationSeconds = $env:K6_ITERATION_SECONDS
$previousDatasetManifest = $env:K6_DATASET_MANIFEST

try {
    $env:K6_BASE_URL = $target.BaseUrl
    $env:K6_ALLOW_REMOTE = if ($target.IsLocal) { 'false' } else { 'true' }
    $env:K6_CONFIRM_TARGET = if ($target.IsLocal) { '' } else { $target.Host }
    $env:K6_LOAD_VUS = [string] $Vus
    $env:K6_ITERATION_SECONDS = [string] $IterationSeconds
    $env:K6_DATASET_MANIFEST = if ($null -eq $datasetManifestPath) {
        ''
    } else {
        (Resolve-Path -LiteralPath $datasetManifestPath).Path.Replace('\', '/')
    }

    $metadata = [ordered]@{
        runId = $run.RunId
        testType = 'load'
        baseUrl = $target.BaseUrl
        targetHost = $target.Host
        peakVus = $Vus
        duration = '5m (30s ramp-up, 4m hold, 30s ramp-down)'
        iterationPacingSeconds = $IterationSeconds
        estimatedReadRequests = $estimatedReadRequests
        setupLoginRequests = 1
        datasetId = $DatasetId
        datasetMeetingCount = $datasetMeetingCount
        startedAtUtc = $startedAt.ToString('o')
        postgresBusinessDataMutation = 'none'
        redisMutation = 'one login session creation'
    }
    $metadata | ConvertTo-Json | Set-Content -LiteralPath $metadataPath -Encoding utf8

    Write-Host "Planned load: peak $Vus VUs, approximately $estimatedReadRequests read requests plus one setup login."
    $arguments = @('run', '--summary-export', $summaryPath, '--tag', "run_id=$($run.RunId)", $scenario)
    $exitCode = Invoke-K6WithGuard -K6Executable $k6 -K6Arguments $arguments `
        -EnableStagingGuard (-not $target.IsLocal) -AwsProfile $AwsProfile -AwsRegion $AwsRegion
    if ($exitCode -ne 0) {
        throw "k6 Load failed with exit code $exitCode."
    }
    $requestCount = Assert-K6Summary -SummaryPath $summaryPath -MinimumHttpRequests 5

    $metadata['exitCode'] = $exitCode
    $metadata['actualRequests'] = $requestCount
    $metadata['finishedAtUtc'] = [DateTime]::UtcNow.ToString('o')
    $metadata | ConvertTo-Json | Set-Content -LiteralPath $metadataPath -Encoding utf8

    Write-Host "Load result: $($run.Path)"
    Write-Host 'Do not start the next VU level until the k6 summary, CloudWatch, and Slack are healthy.'
} finally {
    $env:K6_BASE_URL = $previousBaseUrl
    $env:K6_ALLOW_REMOTE = $previousAllowRemote
    $env:K6_CONFIRM_TARGET = $previousConfirmTarget
    $env:K6_LOAD_VUS = $previousLoadVus
    $env:K6_ITERATION_SECONDS = $previousIterationSeconds
    $env:K6_DATASET_MANIFEST = $previousDatasetManifest
}
