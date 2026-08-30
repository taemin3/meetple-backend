Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Resolve-TestDataTarget {
    param(
        [Parameter(Mandatory = $true)][string] $BaseUrl,
        [Parameter(Mandatory = $true)][bool] $AllowRemote,
        [string] $ConfirmTarget,
        [Parameter(Mandatory = $true)][bool] $RequireRemoteApproval
    )

    if ($BaseUrl -match '[\[\]\(\)]' -or $BaseUrl -match '\s') {
        throw 'BaseUrl must be a plain URL such as https://api.meetple.shop, not a Markdown link.'
    }

    $target = $null
    if (-not [Uri]::TryCreate($BaseUrl, [UriKind]::Absolute, [ref] $target)) {
        throw 'BaseUrl must be an absolute HTTP or HTTPS URL.'
    }
    if ($target.Scheme -notin @('http', 'https')) {
        throw 'BaseUrl must use HTTP or HTTPS.'
    }

    $isLocal = $target.IsLoopback -or $target.Host -in @('localhost', '127.0.0.1', '::1')
    if ($RequireRemoteApproval -and -not $isLocal) {
        if (-not $AllowRemote) {
            throw 'Remote execution is blocked. Pass -AllowRemote only after the staging data mutation is approved.'
        }
        if ($ConfirmTarget -cne $target.Host) {
            throw "ConfirmTarget must exactly match '$($target.Host)'."
        }
    }

    return [pscustomobject]@{
        Host    = $target.Host
        IsLocal = $isLocal
        BaseUrl = $BaseUrl.TrimEnd('/')
    }
}

function Assert-TestCredentials {
    if ([string]::IsNullOrWhiteSpace($env:K6_EMAIL)) {
        throw 'Set K6_EMAIL in the current PowerShell session.'
    }
    if ([string]::IsNullOrWhiteSpace($env:K6_PASSWORD)) {
        throw 'Set K6_PASSWORD in the current PowerShell session.'
    }
}

function Invoke-MeetpleJsonRequest {
    param(
        [Parameter(Mandatory = $true)][string] $Uri,
        [Parameter(Mandatory = $true)][ValidateSet('GET', 'POST', 'PATCH', 'DELETE')][string] $Method,
        [object] $Body,
        [string] $AccessToken
    )

    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($AccessToken)) {
        $headers['Authorization'] = "Bearer $AccessToken"
    }

    $parameters = @{
        Uri         = $Uri
        Method      = $Method
        Headers     = $headers
        ErrorAction = 'Stop'
    }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 10 -Compress
        $parameters['Body'] = [System.Text.Encoding]::UTF8.GetBytes($json)
        $parameters['ContentType'] = 'application/json; charset=utf-8'
    }

    try {
        $response = Invoke-RestMethod @parameters
    } catch {
        $detail = $_.Exception.Message
        if ($null -ne $_.Exception.Response) {
            try {
                $stream = $_.Exception.Response.GetResponseStream()
                if ($null -ne $stream) {
                    $reader = [IO.StreamReader]::new($stream)
                    try {
                        $responseBody = $reader.ReadToEnd()
                        if (-not [string]::IsNullOrWhiteSpace($responseBody)) {
                            $detail += " Response: $responseBody"
                        }
                    } finally {
                        $reader.Dispose()
                    }
                }
            } catch {
                # Keep the original HTTP exception when the error body cannot be read.
            }
        }
        throw "Meetple API request failed: $Method $Uri. $detail"
    }

    $successProperty = $response.PSObject.Properties['success']
    if ($null -ne $successProperty -and $successProperty.Value -ne $true) {
        throw "Meetple API returned an unsuccessful envelope: $Method $Uri"
    }
    return $response
}

function Get-TestAccessToken {
    param([Parameter(Mandatory = $true)][string] $BaseUrl)

    $response = Invoke-MeetpleJsonRequest -Uri "$BaseUrl/api/v1/auth/login" -Method POST -Body ([ordered]@{
        email = $env:K6_EMAIL
        password = $env:K6_PASSWORD
    })
    $token = $response.data.accessToken
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw 'Login response did not contain an access token.'
    }
    return [string] $token
}

function Save-TestDataManifest {
    param(
        [Parameter(Mandatory = $true)][object] $Manifest,
        [Parameter(Mandatory = $true)][string] $Path
    )

    $directory = Split-Path -Parent $Path
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $temporaryPath = "$Path.tmp"
    $json = $Manifest | ConvertTo-Json -Depth 10
    [IO.File]::WriteAllText(
        $temporaryPath,
        $json,
        [System.Text.UTF8Encoding]::new($false)
    )
    Move-Item -LiteralPath $temporaryPath -Destination $Path -Force
}

function Get-TestImageFiles {
    param([Parameter(Mandatory = $true)][string] $ImageDirectory)

    if (-not (Test-Path -LiteralPath $ImageDirectory -PathType Container)) {
        throw "Image directory not found: $ImageDirectory"
    }

    $files = @(Get-ChildItem -LiteralPath $ImageDirectory -File | Where-Object {
        $_.Extension.ToLowerInvariant() -in @('.jpg', '.jpeg', '.png', '.webp')
    } | Sort-Object Name)
    $expectedNames = @(
        'exercise-01.png', 'exercise-02.png', 'exercise-03.png',
        'hobby-01.png', 'hobby-02.png', 'hobby-03.png',
        'study-01.png', 'study-02.png', 'study-03.png'
    )
    if ($files.Count -ne 9 -or (Compare-Object -ReferenceObject $expectedNames -DifferenceObject @($files.Name))) {
        throw 'Image directory must contain exactly the nine approved exercise, hobby, and study PNG files.'
    }
    foreach ($file in $files) {
        if ($file.Length -le 0 -or $file.Length -gt 5MB) {
            throw "Image must be between 1 byte and 5 MiB: $($file.Name)"
        }
    }
    return $files
}

function Get-ImageContentType {
    param([Parameter(Mandatory = $true)][IO.FileInfo] $File)

    switch ($File.Extension.ToLowerInvariant()) {
        '.jpg' { return 'image/jpeg' }
        '.jpeg' { return 'image/jpeg' }
        '.png' { return 'image/png' }
        '.webp' { return 'image/webp' }
        default { throw "Unsupported image extension: $($File.Extension)" }
    }
}

function Invoke-PresignedImagePut {
    param(
        [Parameter(Mandatory = $true)][object] $Upload,
        [Parameter(Mandatory = $true)][IO.FileInfo] $File
    )

    if ([string]$Upload.method -cne 'PUT') {
        throw "Unsupported presigned upload method: $($Upload.method)"
    }
    $headers = @{}
    foreach ($property in $Upload.headers.PSObject.Properties) {
        if ($property.Name -notin @('Content-Type', 'Content-Length')) {
            $headers[$property.Name] = [string] $property.Value
        }
    }
    $contentType = Get-ImageContentType -File $File
    try {
        Invoke-WebRequest -Uri ([string] $Upload.uploadUrl) -Method Put -InFile $File.FullName `
            -ContentType $contentType -Headers $headers -UseBasicParsing -ErrorAction Stop | Out-Null
    } catch {
        throw "S3 image upload failed for $($File.Name): $($_.Exception.Message)"
    }
}

function New-MeetingSeedRequest {
    param(
        [Parameter(Mandatory = $true)][int] $Index,
        [Parameter(Mandatory = $true)][object[]] $Images,
        [Parameter(Mandatory = $true)][string] $Marker
    )

    $categoryDefinitions = @(
        [pscustomobject]@{ Name=[System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('7Jq064+Z')); Prefix='exercise'; Location='Seoul Forest'; Address='Seongdong-gu, Seoul'; Latitude=37.5445; Longitude=127.0374 },
        [pscustomobject]@{ Name=[System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('7Iqk7YSw65SU')); Prefix='study'; Location='Gangnam Study Room'; Address='Gangnam-gu, Seoul'; Latitude=37.4979; Longitude=127.0276 },
        [pscustomobject]@{ Name=[System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('7Leo66+4')); Prefix='hobby'; Location='Hongdae Culture Space'; Address='Mapo-gu, Seoul'; Latitude=37.5563; Longitude=126.9236 }
    )
    $category = $categoryDefinitions[($Index - 1) % $categoryDefinitions.Count]
    $categoryImages = @($Images | Where-Object { $_.fileName -like "$($category.Prefix)-*" })
    if ($categoryImages.Count -ne 3) {
        throw "Manifest does not contain three images for category prefix '$($category.Prefix)'."
    }

    $imageCount = (($Index - 1) % 3) + 1
    $imageStart = [Math]::Floor(($Index - 1) / 3) % 3
    $objectKeys = for ($offset = 0; $offset -lt $imageCount; $offset++) {
        [string] $categoryImages[($imageStart + $offset) % 3].objectKey
    }
    $scheduledAt = (Get-Date).AddDays(7 + (($Index - 1) % 84)).Date.AddHours(10 + (($Index - 1) % 10))

    return [ordered]@{
        title = "$Marker $($category.Prefix) meeting $($Index.ToString('0000'))"
        category = $category.Name
        locationName = "$($category.Location) $((($Index - 1) % 10) + 1)"
        address = "$($category.Address), test address $((($Index - 1) % 100) + 1)"
        latitude = $category.Latitude + ((($Index - 1) % 20) * 0.0001)
        longitude = $category.Longitude + ((($Index - 1) % 20) * 0.0001)
        scheduledAt = $scheduledAt.ToString('yyyy-MM-ddTHH:mm:ss')
        capacity = 10 + (($Index - 1) % 41)
        description = "$Marker dedicated performance-test meeting. dataset index=$Index"
        imageObjectKeys = @($objectKeys)
        endsAt = $scheduledAt.AddHours(2).ToString('yyyy-MM-ddTHH:mm:ss')
    }
}
