[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet(100, 1000)]
    [int] $Count,
    [string] $DatasetId = 'meetple-k6-baseline-v1',
    [string] $BaseUrl = 'http://127.0.0.1:8080',
    [ValidateRange(1, 5)]
    [int] $RequestsPerSecond = 2,
    [switch] $AllowRemote,
    [string] $ConfirmTarget,
    [switch] $AcknowledgeDataCreation,
    [switch] $DryRun,
    [string] $AwsProfile = 'meetple-deploy',
    [string] $AwsRegion = 'ap-northeast-2'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')
. (Join-Path $PSScriptRoot 'TestData.Common.ps1')

if ($DatasetId -notmatch '^[a-zA-Z0-9-]{3,40}$') {
    throw 'DatasetId must contain 3-40 letters, numbers, or hyphens.'
}
if (-not $DryRun -and -not $AcknowledgeDataCreation) {
    throw 'Test data creation is blocked. Pass -AcknowledgeDataCreation only after the mutation plan is approved.'
}

$k6Root = Split-Path -Parent $PSScriptRoot
$imageDirectory = Join-Path $k6Root 'data\images'
$manifestPath = Join-Path $k6Root "data\manifests\$DatasetId.json"
$imageFiles = @(Get-TestImageFiles -ImageDirectory $imageDirectory)
$target = Resolve-TestDataTarget -BaseUrl $BaseUrl -AllowRemote $AllowRemote.IsPresent `
    -ConfirmTarget $ConfirmTarget -RequireRemoteApproval (-not $DryRun)
$existingCount = 0
$manifest = $null

if (Test-Path -LiteralPath $manifestPath) {
    $manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
    if ([string]$manifest.baseUrl -cne $target.BaseUrl) {
        throw "Manifest target '$($manifest.baseUrl)' does not match '$($target.BaseUrl)'."
    }
    $existingCount = @($manifest.meetings).Count
    foreach ($file in $imageFiles) {
        $record = @($manifest.images | Where-Object { $_.fileName -ceq $file.Name })
        if ($record.Count -ne 1 -or [string]$record[0].sha256 -cne (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash) {
            throw "Image file changed after the manifest was created: $($file.Name)"
        }
    }
}

$toCreate = [Math]::Max(0, $Count - $existingCount)
$imageRows = if ($null -eq $manifest) { 9 } else { 0 }
$s3Bytes = if ($null -eq $manifest) { ($imageFiles | Measure-Object Length -Sum).Sum } else { 0 }
$estimatedMeetingImageRows = 0
for ($index = $existingCount + 1; $index -le $Count; $index++) {
    $estimatedMeetingImageRows += (($index - 1) % 3) + 1
}

Write-Host "Dataset: $DatasetId"
Write-Host "Target: $($target.BaseUrl)"
Write-Host "Current meetings: $existingCount"
Write-Host "Target meetings: $Count"
Write-Host "Meetings to create: $toCreate"
Write-Host "New meeting_images rows: approximately $estimatedMeetingImageRows"
Write-Host "New S3 objects: $imageRows ($([Math]::Round($s3Bytes / 1MB, 2)) MiB)"
Write-Host "Pacing: at most $RequestsPerSecond meeting create requests/second"
Write-Host 'External effects: one Redis login session; PostgreSQL/WAL writes; no email, FCM, notification, or Outbox business event.'

if ($DryRun) {
    Write-Host 'Dry run complete. No HTTP request or AWS API call was made.'
    exit 0
}
if ($toCreate -eq 0) {
    Write-Host 'The manifest already contains the requested number of meetings.'
    exit 0
}

Assert-TestCredentials
$guard = $null
if (-not $target.IsLocal) {
    $guard = New-StagingGuardContext -AwsProfile $AwsProfile -AwsRegion $AwsRegion
    Test-StagingGuard -Context $guard
}
$accessToken = Get-TestAccessToken -BaseUrl $target.BaseUrl

if ($null -eq $manifest) {
    $uploadRequests = @($imageFiles | ForEach-Object {
        [ordered]@{
            purpose = 'MEETING'
            fileName = $_.Name
            contentType = Get-ImageContentType -File $_
            contentLength = $_.Length
        }
    })
    $uploadResponse = Invoke-MeetpleJsonRequest -Uri "$($target.BaseUrl)/api/v1/images/upload-urls" `
        -Method POST -AccessToken $accessToken -Body ([ordered]@{ images = $uploadRequests })
    $uploads = @($uploadResponse.data)
    if ($uploads.Count -ne $imageFiles.Count) {
        throw "Expected $($imageFiles.Count) presigned uploads but received $($uploads.Count)."
    }

    $imageManifestRows = @()
    for ($index = 0; $index -lt $imageFiles.Count; $index++) {
        Invoke-PresignedImagePut -Upload $uploads[$index] -File $imageFiles[$index]
        $imageManifestRows += [ordered]@{
            fileName = $imageFiles[$index].Name
            contentType = Get-ImageContentType -File $imageFiles[$index]
            contentLength = $imageFiles[$index].Length
            sha256 = (Get-FileHash -LiteralPath $imageFiles[$index].FullName -Algorithm SHA256).Hash
            objectKey = [string] $uploads[$index].objectKey
            fileUrl = [string] $uploads[$index].fileUrl
        }
    }
    $manifest = [ordered]@{
        schemaVersion = 1
        datasetId = $DatasetId
        marker = '[K6-BL-V1]'
        baseUrl = $target.BaseUrl
        targetHost = $target.Host
        createdAtUtc = [DateTime]::UtcNow.ToString('o')
        images = @($imageManifestRows)
        meetings = @()
        completed = $false
    }
    Save-TestDataManifest -Manifest $manifest -Path $manifestPath
}

$intervalMilliseconds = [Math]::Ceiling(1000.0 / $RequestsPerSecond)
$nextGuardCheck = [DateTime]::UtcNow.AddSeconds(30)
for ($index = $existingCount + 1; $index -le $Count; $index++) {
    $iterationStarted = [DateTime]::UtcNow
    if ($null -ne $guard -and [DateTime]::UtcNow -ge $nextGuardCheck) {
        Test-StagingGuard -Context $guard
        $nextGuardCheck = [DateTime]::UtcNow.AddSeconds(30)
    }

    $request = New-MeetingSeedRequest -Index $index -Images @($manifest.images) -Marker ([string]$manifest.marker)
    $response = Invoke-MeetpleJsonRequest -Uri "$($target.BaseUrl)/api/v1/meetings" `
        -Method POST -AccessToken $accessToken -Body $request
    $meetingId = $response.data.id
    if ($null -eq $meetingId -or [string]$meetingId -notmatch '^\d+$') {
        throw "Meeting create response did not contain a numeric ID for dataset index $index."
    }

    $entry = [ordered]@{
        index = $index
        id = [long] $meetingId
        title = [string] $request.title
        category = [string] $request.category
        imageObjectKeys = @($request.imageObjectKeys)
        createdAtUtc = [DateTime]::UtcNow.ToString('o')
        deletedAtUtc = $null
    }
    $manifest.meetings = @($manifest.meetings) + $entry
    $manifest.completed = (@($manifest.meetings).Count -ge $Count)
    $manifest.lastUpdatedAtUtc = [DateTime]::UtcNow.ToString('o')
    Save-TestDataManifest -Manifest $manifest -Path $manifestPath

    if ($index % 25 -eq 0 -or $index -eq $Count) {
        Write-Host "Created $index / $Count meetings. Latest ID: $meetingId"
    }
    $elapsedMilliseconds = ([DateTime]::UtcNow - $iterationStarted).TotalMilliseconds
    $remainingMilliseconds = $intervalMilliseconds - $elapsedMilliseconds
    if ($remainingMilliseconds -gt 0 -and $index -lt $Count) {
        Start-Sleep -Milliseconds ([int]$remainingMilliseconds)
    }
}

if ($null -ne $guard) {
    Test-StagingGuard -Context $guard
}
Write-Host "Seed completed. Manifest: $manifestPath"
Write-Host 'Do not run the next load stage until CloudWatch, WAL, ECS tasks, and Slack are checked.'
