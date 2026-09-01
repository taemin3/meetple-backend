[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet(300, 400, 500)]
    [int] $TargetRps,
    [string] $BaseUrl = 'http://127.0.0.1:8080',
    [switch] $AllowRemote,
    [string] $ConfirmTarget,
    [switch] $AcknowledgeAuthProbe,
    [string] $AwsProfile = 'meetple-deploy',
    [string] $AwsRegion = 'ap-northeast-2'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')

if (-not $AcknowledgeAuthProbe) {
    throw 'Auth probe execution is blocked. Pass -AcknowledgeAuthProbe only after the exact RPS is approved.'
}

$k6Root = Split-Path -Parent $PSScriptRoot
$scenario = Join-Path $k6Root 'scenarios\auth-probe.js'
$target = Assert-K6Environment -BaseUrl $BaseUrl -AllowRemote $AllowRemote.IsPresent `
    -ConfirmTarget $ConfirmTarget -RequireMeetingId $false
$k6 = Get-K6Executable
$run = New-K6ResultDirectory -K6Root $k6Root -TestType "auth-probe-$TargetRps-rps"
$summaryPath = Join-Path $run.Path 'k6-summary.json'
$metadataPath = Join-Path $run.Path 'run-metadata.json'
$startedAt = [DateTime]::UtcNow
$estimatedRequests = $TargetRps * 150

$previousBaseUrl = $env:K6_BASE_URL
$previousAllowRemote = $env:K6_ALLOW_REMOTE
$previousConfirmTarget = $env:K6_CONFIRM_TARGET
$previousTargetRps = $env:K6_AUTH_PROBE_TARGET_RPS

try {
    $env:K6_BASE_URL = $target.BaseUrl
    $env:K6_ALLOW_REMOTE = if ($target.IsLocal) { 'false' } else { 'true' }
    $env:K6_CONFIRM_TARGET = if ($target.IsLocal) { '' } else { $target.Host }
    $env:K6_AUTH_PROBE_TARGET_RPS = [string] $TargetRps

    $metadata = [ordered]@{
        runId = $run.RunId
        testType = 'auth-probe'
        endpoint = 'auth-probe'
        baseUrl = $target.BaseUrl
        targetHost = $target.Host
        targetRps = $TargetRps
        duration = '3m (30s ramp-up, 2m hold, 30s ramp-down)'
        estimatedRequests = $estimatedRequests
        setupLoginRequests = 1
        startedAtUtc = $startedAt.ToString('o')
        postgresBusinessDataMutation = 'none'
        redisMutation = 'one login session creation'
        databaseCallsPerProbeRequest = 0
    }
    $metadata | ConvertTo-Json | Set-Content -LiteralPath $metadataPath -Encoding utf8

    Write-Host "Planned authenticated probe: $TargetRps RPS, approximately $estimatedRequests probe requests plus one setup login."
    Write-Host 'Duration: 30s ramp-up, 2m hold, 30s ramp-down.'
    Write-Host 'Each probe performs JWT parsing and Redis session validation, then returns HTTP 204 without DB/JPA access.'
    Write-Host 'External effects: one Redis login session; no PostgreSQL business mutation, email, FCM, Outbox, or Kafka event.'

    $arguments = @('run', '--summary-export', $summaryPath, '--tag', "run_id=$($run.RunId)", $scenario)
    $exitCode = Invoke-K6WithGuard -K6Executable $k6 -K6Arguments $arguments `
        -EnableStagingGuard (-not $target.IsLocal) -AwsProfile $AwsProfile -AwsRegion $AwsRegion
    $requestCount = Assert-K6Summary -SummaryPath $summaryPath -MinimumHttpRequests 100

    $metadata['exitCode'] = $exitCode
    $metadata['actualRequests'] = $requestCount
    $metadata['finishedAtUtc'] = [DateTime]::UtcNow.ToString('o')
    $metadata | ConvertTo-Json | Set-Content -LiteralPath $metadataPath -Encoding utf8

    Write-Host "Auth probe result: $($run.Path)"
    Write-Host 'Do not start the next RPS level until k6, CloudWatch, ECS tasks, Redis latency, and Slack are healthy.'
    if ($exitCode -ne 0) {
        throw "k6 auth probe failed with exit code $exitCode."
    }
} finally {
    $env:K6_BASE_URL = $previousBaseUrl
    $env:K6_ALLOW_REMOTE = $previousAllowRemote
    $env:K6_CONFIRM_TARGET = $previousConfirmTarget
    $env:K6_AUTH_PROBE_TARGET_RPS = $previousTargetRps
}
