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
    throw "AI_MODERATION_SERVICE_TOKEN 환경변수에 32자 이상의 서비스 키가 필요합니다."
}

$uri = $AiBaseUrl.TrimEnd('/') + "/v1/moderation/policies/embeddings/sync"
$headers = @{ "X-AI-Service-Token" = $serviceToken }
$totalEmbedded = 0

for ($batch = 1; $batch -le $MaxBatches; $batch++) {
    $response = Invoke-RestMethod `
        -Method Post `
        -Uri $uri `
        -Headers $headers `
        -ContentType "application/json" `
        -Body (@{ limit = $BatchSize } | ConvertTo-Json)

    $totalEmbedded += [int]$response.embeddedCount
    Write-Host "배치 $batch 완료: $($response.embeddedCount)개 임베딩 저장"

    if ([int]$response.requestedCount -eq 0) {
        Write-Host "정책 임베딩 동기화 완료: 총 $totalEmbedded개"
        exit 0
    }
}

throw "최대 배치 수($MaxBatches)에 도달했습니다. 남은 작업을 확인한 뒤 다시 실행하세요."
