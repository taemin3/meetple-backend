[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $Manifest,
    [Parameter(Mandatory = $true)]
    [ValidateSet('focused', 'distributed')]
    [string] $Scenario,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^chat-staging-[A-Za-z0-9-]{1,51}$')]
    [string] $RunId,
    [switch] $Smoke,
    [ValidateRange(1, 100)]
    [int] $Rps = 20,
    [ValidateRange(1, 60)]
    [int] $DurationSeconds = 60,
    [ValidateRange(0, 60)]
    [int] $WarmupSeconds = 10,
    [ValidateRange(1, 300)]
    [int] $SettleSeconds = 120,
    [ValidateRange(1, 10)]
    [int] $SubscribersPerRoom = 10,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^api\.meetple\.shop$')]
    [string] $ConfirmTarget,
    [switch] $AcknowledgeStagingLoad,
    [string] $NodeExecutable = 'C:\Users\ruhok\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $Manifest -PathType Leaf)) {
    throw "Manifest not found: $Manifest"
}
$fixture = Get-Content -Raw -LiteralPath $Manifest | ConvertFrom-Json
$target = [Uri]([string] $fixture.baseUrl)
if ($target.Scheme -cne 'https' -or $target.Host -cne 'api.meetple.shop') {
    throw 'The staging runner only allows https://api.meetple.shop.'
}
if ($ConfirmTarget -cne $target.Host) {
    throw "ConfirmTarget must exactly match '$($target.Host)'."
}
if ([string] $fixture.targetEnvironment -cne 'staging' -or
        [string] $fixture.fixtureKind -cne 'chat-load-v1' -or
        [int] $fixture.pushDeviceCount -ne 0) {
    throw 'Use a staging chat-load-v1 manifest with pushDeviceCount=0.'
}

if ($Smoke) {
    $Rps = 2
    $DurationSeconds = 10
    $WarmupSeconds = 2
}
$scheduled = $Rps * $DurationSeconds
if ($scheduled -gt 6000) {
    throw 'A single staging condition may schedule at most 6,000 messages.'
}
if (-not $AcknowledgeStagingLoad) {
    throw @"
Staging load is blocked. Review this plan, then pass -AcknowledgeStagingLoad:
Target: $($target.GetLeftPart([UriPartial]::Authority))
RunId: $RunId
Load: $Rps message(s)/second x $DurationSeconds second(s) = $scheduled scheduled messages
Scenario: $Scenario; subscribers per room: $SubscribersPerRoom
Effects: staging PostgreSQL chat/read-state/Outbox writes and Redis fan-out.
Push safety: fixture declares zero push devices; stop if an actual FCM send is observed.
"@
}

if (-not (Test-Path -LiteralPath $NodeExecutable)) {
    $node = Get-Command node -ErrorAction SilentlyContinue
    if ($null -eq $node) {
        throw 'Node.js 22 or newer is required.'
    }
    $NodeExecutable = $node.Source
}

$scriptPath = Join-Path $PSScriptRoot 'chat-load.mjs'
$resultRoot = Join-Path $PSScriptRoot 'results\chat-staging'
New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null
$output = Join-Path $resultRoot "$RunId.json"
if (Test-Path -LiteralPath $output) {
    throw "Result already exists for this RunId: $output"
}

Write-Host "Target: $($target.GetLeftPart([UriPartial]::Authority)) (staging allowlist confirmed)"
Write-Host "Load: $Rps message(s)/second x $DurationSeconds second(s) = $scheduled scheduled messages"
Write-Host "Condition: $Scenario; 10 clients; $SubscribersPerRoom subscriber(s) per room"
Write-Host 'Observe ECS/RDS/Hikari and run Capture-ChatStagingPostgresLocks.ps1 in another terminal.'
Write-Host 'Stop criteria: any send/STOMP error, missing history row, incomplete receipt, duplicate receipt, or invalid sequence.'

& $NodeExecutable $scriptPath `
    --manifest $Manifest `
    --target-mode staging `
    --confirm-target $ConfirmTarget `
    --scenario $Scenario `
    --run-id $RunId `
    --rps $Rps `
    --duration $DurationSeconds `
    --warmup-seconds $WarmupSeconds `
    --settle-seconds $SettleSeconds `
    --subscribers-per-room $SubscribersPerRoom `
    --output $output
if ($LASTEXITCODE -ne 0) {
    throw "Staging chat load failed for $RunId with exit code $LASTEXITCODE."
}

$result = Get-Content -Raw -LiteralPath $output | ConvertFrom-Json
$invalidSequences = @($result.delivery.sequenceChecks | Where-Object {
    $_.duplicateSequences -gt 0 -or $_.gaps -gt 0
})
if ($result.transmission.unsent -gt 0 -or
        @($result.delivery.stompErrors).Count -gt 0 -or
        @($result.delivery.missingFromDatabase).Count -gt 0 -or
        @($result.delivery.storedButNotReceived).Count -gt 0 -or
        $result.delivery.incompleteObserverMessages -gt 0 -or
        $result.delivery.duplicateReceipts -gt 0 -or
        $invalidSequences.Count -gt 0) {
    throw "Stop criteria reached for $RunId. Inspect $output before continuing."
}

Write-Host "Completed. Result: $output"
