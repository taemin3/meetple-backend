[CmdletBinding()]
param(
    [string] $BaseUrl = 'http://127.0.0.1:8080',
    [switch] $AllowRemote,
    [string] $ConfirmTarget,
    [string] $AwsProfile = 'meetple-deploy',
    [string] $AwsRegion = 'ap-northeast-2'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')

$k6Root = Split-Path -Parent $PSScriptRoot
$scenario = Join-Path $k6Root 'scenarios\smoke.js'
$target = Assert-K6Environment -BaseUrl $BaseUrl -AllowRemote $AllowRemote.IsPresent `
    -ConfirmTarget $ConfirmTarget -RequireMeetingId $false
$k6 = Get-K6Executable
$run = New-K6ResultDirectory -K6Root $k6Root -TestType 'smoke'
$summaryPath = Join-Path $run.Path 'k6-summary.json'
$metadataPath = Join-Path $run.Path 'run-metadata.json'
$startedAt = [DateTime]::UtcNow

$previousBaseUrl = $env:K6_BASE_URL
$previousAllowRemote = $env:K6_ALLOW_REMOTE
$previousConfirmTarget = $env:K6_CONFIRM_TARGET

try {
    $env:K6_BASE_URL = $target.BaseUrl
    $env:K6_ALLOW_REMOTE = if ($target.IsLocal) { 'false' } else { 'true' }
    $env:K6_CONFIRM_TARGET = if ($target.IsLocal) { '' } else { $target.Host }

    $metadata = [ordered]@{
        runId = $run.RunId
        testType = 'smoke'
        baseUrl = $target.BaseUrl
        targetHost = $target.Host
        vus = 1
        iterations = 1
        expectedRequestsMaximum = 8
        startedAtUtc = $startedAt.ToString('o')
        postgresBusinessDataMutation = 'none'
        redisMutation = 'login session creation and token rotation'
    }
    $metadata | ConvertTo-Json | Set-Content -LiteralPath $metadataPath -Encoding utf8

    $arguments = @('run', '--summary-export', $summaryPath, '--tag', "run_id=$($run.RunId)", $scenario)
    $exitCode = Invoke-K6WithGuard -K6Executable $k6 -K6Arguments $arguments `
        -EnableStagingGuard (-not $target.IsLocal) -AwsProfile $AwsProfile -AwsRegion $AwsRegion
    if ($exitCode -ne 0) {
        throw "k6 Smoke failed with exit code $exitCode."
    }
    $requestCount = Assert-K6Summary -SummaryPath $summaryPath -MinimumHttpRequests 8

    $metadata['exitCode'] = $exitCode
    $metadata['actualRequests'] = $requestCount
    $metadata['finishedAtUtc'] = [DateTime]::UtcNow.ToString('o')
    $metadata | ConvertTo-Json | Set-Content -LiteralPath $metadataPath -Encoding utf8

    Write-Host "Smoke result: $($run.Path)"
    Write-Host 'Wait about two minutes for CloudWatch ingestion, then capture metrics with Capture-CloudWatch.ps1.'
} finally {
    $env:K6_BASE_URL = $previousBaseUrl
    $env:K6_ALLOW_REMOTE = $previousAllowRemote
    $env:K6_CONFIRM_TARGET = $previousConfirmTarget
}
