[CmdletBinding()]
param(
    [string]$AiBaseUrl = "http://127.0.0.1:8001",
    [ValidateRange(1, 100)]
    [int]$BatchSize = 50,
    [ValidateRange(1, 1000)]
    [int]$MaxBatches = 100
)

$ErrorActionPreference = "Stop"
$serviceToken = $env:AI_MODERATION_SERVICE_TOKEN
if ([string]::IsNullOrWhiteSpace($serviceToken) -or $serviceToken.Length -lt 32) {
    throw "AI_MODERATION_SERVICE_TOKEN must contain at least 32 characters."
}

$uri = $AiBaseUrl.TrimEnd('/') + "/v1/moderation/policies/embeddings/sync"
$headers = @{ "X-AI-Service-Token" = $serviceToken }
$totalEmbedded = 0

for ($batch = 1; $batch -le ($MaxBatches + 1); $batch++) {
    $response = Invoke-RestMethod `
        -Method Post `
        -Uri $uri `
        -Headers $headers `
        -ContentType "application/json" `
        -Body (@{ limit = $BatchSize } | ConvertTo-Json)

    if ([int]$response.requestedCount -eq 0) {
        Write-Host "Policy embedding sync completed: $totalEmbedded embedded."
        exit 0
    }

    $totalEmbedded += [int]$response.embeddedCount
    Write-Host "Batch $batch completed: $($response.embeddedCount) embedded."

    if ($batch -gt $MaxBatches) {
        break
    }
}

throw "Reached the maximum batch count ($MaxBatches) with remaining work. Run the script again."
