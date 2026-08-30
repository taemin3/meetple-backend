[CmdletBinding()]
param(
    [string] $DatasetId = 'meetple-k6-baseline-v1',
    [string] $BaseUrl = 'http://127.0.0.1:8080',
    [ValidateRange(1, 5)]
    [int] $RequestsPerSecond = 2,
    [switch] $AllowRemote,
    [string] $ConfirmTarget,
    [switch] $AcknowledgeCleanup,
    [switch] $DryRun,
    [string] $AwsProfile = 'meetple-deploy',
    [string] $AwsRegion = 'ap-northeast-2'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')
. (Join-Path $PSScriptRoot 'TestData.Common.ps1')

if (-not $DryRun -and -not $AcknowledgeCleanup) {
    throw 'Cleanup is blocked. Pass -AcknowledgeCleanup only after the exact manifest is reviewed.'
}

$k6Root = Split-Path -Parent $PSScriptRoot
$manifestPath = Join-Path $k6Root "data\manifests\$DatasetId.json"
if (-not (Test-Path -LiteralPath $manifestPath)) {
    throw "Manifest not found: $manifestPath"
}
$manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
$target = Resolve-TestDataTarget -BaseUrl $BaseUrl -AllowRemote $AllowRemote.IsPresent `
    -ConfirmTarget $ConfirmTarget -RequireRemoteApproval (-not $DryRun)
if ([string]$manifest.baseUrl -cne $target.BaseUrl) {
    throw "Manifest target '$($manifest.baseUrl)' does not match '$($target.BaseUrl)'."
}

$pendingMeetings = @($manifest.meetings | Where-Object { $null -eq $_.deletedAtUtc })
Write-Host "Dataset: $DatasetId"
Write-Host "Manifest: $manifestPath"
Write-Host "Meetings recorded: $(@($manifest.meetings).Count)"
Write-Host "Meetings to soft-delete: $($pendingMeetings.Count)"
Write-Host "S3 objects retained until the normal purge path: $(@($manifest.images).Count)"
Write-Host 'Delayed side effect: after the 30-day retention period, purge can publish image-deletion Outbox events.'

if ($DryRun) {
    Write-Host 'Dry run complete. No HTTP request or AWS API call was made.'
    exit 0
}
if ($pendingMeetings.Count -eq 0) {
    Write-Host 'All manifest meetings are already marked as deleted.'
    exit 0
}

Assert-TestCredentials
$guard = $null
if (-not $target.IsLocal) {
    $guard = New-StagingGuardContext -AwsProfile $AwsProfile -AwsRegion $AwsRegion
    Test-StagingGuard -Context $guard
}
$accessToken = Get-TestAccessToken -BaseUrl $target.BaseUrl
$intervalMilliseconds = [Math]::Ceiling(1000.0 / $RequestsPerSecond)
$nextGuardCheck = [DateTime]::UtcNow.AddSeconds(30)

foreach ($meeting in $pendingMeetings) {
    $iterationStarted = [DateTime]::UtcNow
    if ($null -ne $guard -and [DateTime]::UtcNow -ge $nextGuardCheck) {
        Test-StagingGuard -Context $guard
        $nextGuardCheck = [DateTime]::UtcNow.AddSeconds(30)
    }

    Invoke-MeetpleJsonRequest -Uri "$($target.BaseUrl)/api/v1/meetings/$($meeting.id)" `
        -Method DELETE -AccessToken $accessToken | Out-Null
    $meeting.deletedAtUtc = [DateTime]::UtcNow.ToString('o')
    $manifest.lastUpdatedAtUtc = [DateTime]::UtcNow.ToString('o')
    Save-TestDataManifest -Manifest $manifest -Path $manifestPath

    $elapsedMilliseconds = ([DateTime]::UtcNow - $iterationStarted).TotalMilliseconds
    $remainingMilliseconds = $intervalMilliseconds - $elapsedMilliseconds
    if ($remainingMilliseconds -gt 0) {
        Start-Sleep -Milliseconds ([int]$remainingMilliseconds)
    }
}

if ($null -ne $guard) {
    Test-StagingGuard -Context $guard
}
Write-Host 'Cleanup completed. Meetings are soft-deleted; physical purge and image deletion follow the 30-day retention policy.'
