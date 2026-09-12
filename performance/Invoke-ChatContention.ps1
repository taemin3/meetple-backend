[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $Manifest,
    [Parameter(Mandatory = $true)]
    [ValidateSet('focused', 'distributed')]
    [string] $Scenario,
    [switch] $Smoke,
    [string] $RunId,
    [string] $NodeExecutable = 'C:\Users\ruhok\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $NodeExecutable)) {
    $node = Get-Command node -ErrorAction SilentlyContinue
    if ($null -eq $node) {
        throw 'Node.js 22 or newer is required.'
    }
    $NodeExecutable = $node.Source
}

$scriptPath = Join-Path $PSScriptRoot 'chat-load.mjs'
$resultRoot = Join-Path $PSScriptRoot 'results\chat-contention'
New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null

$rps = if ($Smoke) { 2 } else { 20 }
$duration = if ($Smoke) { 10 } else { 60 }
$warmup = if ($Smoke) { 2 } else { 10 }
if ([string]::IsNullOrWhiteSpace($RunId)) {
    $RunId = "chat-$Scenario-$(Get-Date -Format 'yyyyMMddHHmmss')"
}
$output = Join-Path $resultRoot "$RunId.json"

Write-Host 'Target: local only (validated again by chat-load.mjs)'
Write-Host "Load: $rps message(s)/second x $duration second(s) = $($rps * $duration) scheduled messages"
Write-Host "Condition: $Scenario; 10 clients; all 10 clients subscribed to all 10 rooms"
Write-Host 'Effects: PostgreSQL chat/read-state/Outbox writes and local Redis fan-out; actual FCM remains disabled.'
Write-Host 'Stop criteria: any process error, unsent message, STOMP error, missing DB row, or stored-but-not-received message.'
Write-Host 'Run Capture-ChatPostgresLocks.ps1 in a second terminal for this condition.'

& $NodeExecutable $scriptPath `
    --manifest $Manifest `
    --scenario $Scenario `
    --run-id $RunId `
    --rps $rps `
    --duration $duration `
    --warmup-seconds $warmup `
    --output $output
if ($LASTEXITCODE -ne 0) {
    throw "Chat load failed for $RunId with exit code $LASTEXITCODE."
}

$result = Get-Content -Raw -LiteralPath $output | ConvertFrom-Json
if ($result.transmission.unsent -gt 0 -or
        $result.delivery.stompErrors.Count -gt 0 -or
        $result.delivery.missingFromDatabase.Count -gt 0 -or
        $result.delivery.storedButNotReceived.Count -gt 0 -or
        $result.delivery.missingCommitObservation.Count -gt 0 -or
        $result.delivery.commitIdMismatches.Count -gt 0 -or
        $result.delivery.incompleteObserverMessages -gt 0) {
    throw "Stop criteria reached for $RunId. Inspect $output before continuing."
}

Write-Host "Completed. Result: $output"
