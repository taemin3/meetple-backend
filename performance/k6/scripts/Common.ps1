Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:StagingCluster = 'meetple-staging-cluster'
$script:BackendService = 'meetple-staging-backend'
$script:EventRuntimeService = 'meetple-staging-event-runtime'
$script:StagingAlbName = 'meetple-staging-alb'
$script:StagingTargetGroupName = 'meetple-staging-app'

function Get-K6Executable {
    $command = Get-Command k6 -ErrorAction SilentlyContinue
    if ($null -ne $command) {
        return $command.Source
    }

    $installedCandidates = @(
        (Join-Path $env:ProgramFiles 'k6\k6.exe'),
        (Join-Path ${env:ProgramFiles(x86)} 'k6\k6.exe'),
        (Join-Path $env:LOCALAPPDATA 'Programs\k6\k6.exe')
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    foreach ($candidate in $installedCandidates) {
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }

    if ($null -eq $command) {
        throw @'
k6 is not installed or is not available on PATH.
Install the Windows package or standalone binary from:
https://grafana.com/docs/k6/latest/set-up/install-k6/
Then open a new PowerShell window and run: k6 version
'@
    }
}

function Invoke-AwsJson {
    param([Parameter(Mandatory = $true)][string[]] $Arguments)

    $raw = & aws @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "AWS CLI command failed: aws $($Arguments -join ' ')"
    }
    return $raw | ConvertFrom-Json
}

function Assert-K6Environment {
    param(
        [Parameter(Mandatory = $true)][string] $BaseUrl,
        [Parameter(Mandatory = $true)][bool] $AllowRemote,
        [string] $ConfirmTarget,
        [Parameter(Mandatory = $true)][bool] $RequireMeetingId
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
    if (-not $isLocal) {
        if (-not $AllowRemote) {
            throw 'Remote execution is blocked. Pass -AllowRemote only after the staging run is approved.'
        }
        if ($ConfirmTarget -cne $target.Host) {
            throw "ConfirmTarget must exactly match '$($target.Host)'."
        }
    }

    if ([string]::IsNullOrWhiteSpace($env:K6_EMAIL)) {
        throw 'Set K6_EMAIL in the current PowerShell session.'
    }
    if ([string]::IsNullOrWhiteSpace($env:K6_PASSWORD)) {
        throw 'Set K6_PASSWORD in the current PowerShell session.'
    }
    if ($RequireMeetingId -and $env:K6_MEETING_ID -notmatch '^\d+$') {
        throw 'Set K6_MEETING_ID to the numeric ID of a dedicated staging meeting.'
    }

    return [pscustomobject]@{
        Host    = $target.Host
        IsLocal = $isLocal
        BaseUrl = $BaseUrl.TrimEnd('/')
    }
}

function Assert-K6Summary {
    param(
        [Parameter(Mandatory = $true)][string] $SummaryPath,
        [Parameter(Mandatory = $true)][int] $MinimumHttpRequests
    )

    if (-not (Test-Path -LiteralPath $SummaryPath)) {
        throw 'k6 did not create a summary file.'
    }

    $summary = Get-Content -Raw -LiteralPath $SummaryPath | ConvertFrom-Json
    $httpRequestsMetric = $summary.metrics.PSObject.Properties['http_reqs']
    if ($null -eq $httpRequestsMetric) {
        throw 'k6 completed without sending any HTTP requests.'
    }

    $metricValue = $httpRequestsMetric.Value
    $valuesProperty = $metricValue.PSObject.Properties['values']
    if ($null -ne $valuesProperty) {
        $metricValue = $valuesProperty.Value
    }
    $countProperty = $metricValue.PSObject.Properties['count']
    if ($null -eq $countProperty) {
        throw 'k6 summary does not contain the HTTP request count.'
    }

    $requestCount = [int] $countProperty.Value
    if ($requestCount -lt $MinimumHttpRequests) {
        throw "k6 sent only $requestCount HTTP requests; at least $MinimumHttpRequests were required."
    }
    return $requestCount
}

function New-K6ResultDirectory {
    param(
        [Parameter(Mandatory = $true)][string] $K6Root,
        [Parameter(Mandatory = $true)][string] $TestType
    )

    $runId = "{0}-{1}" -f (Get-Date -Format 'yyyyMMdd-HHmmss'), $TestType
    $resultDirectory = Join-Path $K6Root "results\$runId"
    New-Item -ItemType Directory -Path $resultDirectory -Force | Out-Null
    return [pscustomobject]@{ RunId = $runId; Path = $resultDirectory }
}

function New-StagingGuardContext {
    param(
        [Parameter(Mandatory = $true)][string] $AwsProfile,
        [Parameter(Mandatory = $true)][string] $AwsRegion
    )

    Invoke-AwsJson -Arguments @(
        'sts', 'get-caller-identity', '--profile', $AwsProfile,
        '--region', $AwsRegion, '--output', 'json'
    ) | Out-Null

    $loadBalancer = Invoke-AwsJson -Arguments @(
        'elbv2', 'describe-load-balancers', '--names', $script:StagingAlbName,
        '--profile', $AwsProfile, '--region', $AwsRegion, '--output', 'json'
    )
    $targetGroup = Invoke-AwsJson -Arguments @(
        'elbv2', 'describe-target-groups', '--names', $script:StagingTargetGroupName,
        '--profile', $AwsProfile, '--region', $AwsRegion, '--output', 'json'
    )
    $backendTasks = Invoke-AwsJson -Arguments @(
        'ecs', 'list-tasks', '--cluster', $script:StagingCluster,
        '--service-name', $script:BackendService, '--desired-status', 'RUNNING',
        '--profile', $AwsProfile, '--region', $AwsRegion, '--output', 'json'
    )

    return [pscustomobject]@{
        AwsProfile            = $AwsProfile
        AwsRegion             = $AwsRegion
        StartedAtUtc          = [DateTime]::UtcNow
        BackendTaskArns       = @($backendTasks.taskArns | Sort-Object)
        LoadBalancerArnSuffix = $loadBalancer.LoadBalancers[0].LoadBalancerArn -replace '^.*:loadbalancer/', ''
        TargetGroupArnSuffix  = $targetGroup.TargetGroups[0].TargetGroupArn -replace '^.*:targetgroup/', 'targetgroup/'
    }
}

function Test-StagingGuard {
    param([Parameter(Mandatory = $true)][pscustomobject] $Context)

    $services = Invoke-AwsJson -Arguments @(
        'ecs', 'describe-services', '--cluster', $script:StagingCluster,
        '--services', $script:BackendService, $script:EventRuntimeService,
        '--profile', $Context.AwsProfile, '--region', $Context.AwsRegion, '--output', 'json'
    )
    foreach ($service in $services.services) {
        if ($service.runningCount -lt $service.desiredCount -or $service.pendingCount -gt 0) {
            throw "ECS service is not stable: $($service.serviceName) desired=$($service.desiredCount) running=$($service.runningCount) pending=$($service.pendingCount)"
        }
    }

    $backendTasks = Invoke-AwsJson -Arguments @(
        'ecs', 'list-tasks', '--cluster', $script:StagingCluster,
        '--service-name', $script:BackendService, '--desired-status', 'RUNNING',
        '--profile', $Context.AwsProfile, '--region', $Context.AwsRegion, '--output', 'json'
    )
    $currentTaskArns = @($backendTasks.taskArns | Sort-Object)
    if (($currentTaskArns -join '|') -cne ($Context.BackendTaskArns -join '|')) {
        throw 'Backend task changed during the test. A restart or deployment may have occurred.'
    }

    $alarms = Invoke-AwsJson -Arguments @(
        'cloudwatch', 'describe-alarms', '--alarm-name-prefix', 'meetple-staging-',
        '--state-value', 'ALARM', '--profile', $Context.AwsProfile,
        '--region', $Context.AwsRegion, '--output', 'json'
    )
    $alarmNames = @($alarms.MetricAlarms | ForEach-Object { $_.AlarmName })
    if ($alarmNames.Count -gt 0) {
        throw "CloudWatch alarm entered ALARM: $($alarmNames -join ', ')"
    }

    $endTime = [DateTime]::UtcNow
    $fiveXx = Invoke-AwsJson -Arguments @(
        'cloudwatch', 'get-metric-statistics', '--namespace', 'AWS/ApplicationELB',
        '--metric-name', 'HTTPCode_Target_5XX_Count',
        '--start-time', $Context.StartedAtUtc.ToString('yyyy-MM-ddTHH:mm:ssZ'),
        '--end-time', $endTime.ToString('yyyy-MM-ddTHH:mm:ssZ'),
        '--period', '60', '--statistics', 'Sum', '--dimensions',
        "Name=LoadBalancer,Value=$($Context.LoadBalancerArnSuffix)",
        "Name=TargetGroup,Value=$($Context.TargetGroupArnSuffix)",
        '--profile', $Context.AwsProfile, '--region', $Context.AwsRegion, '--output', 'json'
    )
    $targetFiveXxCount = 0.0
    foreach ($datapoint in @($fiveXx.Datapoints)) {
        $sumProperty = $datapoint.PSObject.Properties['Sum']
        if ($null -ne $sumProperty -and $null -ne $sumProperty.Value) {
            $targetFiveXxCount += [double] $sumProperty.Value
        }
    }
    if ($targetFiveXxCount -gt 0) {
        throw "ALB observed $targetFiveXxCount target 5xx responses after the test started."
    }
}

function Invoke-K6WithGuard {
    param(
        [Parameter(Mandatory = $true)][string] $K6Executable,
        [Parameter(Mandatory = $true)][string[]] $K6Arguments,
        [Parameter(Mandatory = $true)][bool] $EnableStagingGuard,
        [string] $AwsProfile = 'meetple-deploy',
        [string] $AwsRegion = 'ap-northeast-2'
    )

    $guard = $null
    if ($EnableStagingGuard) {
        $guard = New-StagingGuardContext -AwsProfile $AwsProfile -AwsRegion $AwsRegion
        Test-StagingGuard -Context $guard
    }

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $K6Executable
    $startInfo.UseShellExecute = $false
    foreach ($argument in $K6Arguments) {
        if ($argument -match '[\s"]') {
            throw "Unsupported whitespace or quote in k6 process argument: $argument"
        }
    }
    $startInfo.Arguments = $K6Arguments -join ' '
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw 'Failed to start the k6 process.'
    }
    $guardFailure = $null
    $nextGuardCheck = [DateTime]::UtcNow.AddSeconds(30)

    try {
        while (-not $process.HasExited) {
            Start-Sleep -Seconds 2
            if ($EnableStagingGuard -and [DateTime]::UtcNow -ge $nextGuardCheck) {
                try {
                    Test-StagingGuard -Context $guard
                } catch {
                    $guardFailure = $_.Exception.Message
                    Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
                    break
                }
                $nextGuardCheck = [DateTime]::UtcNow.AddSeconds(30)
            }
        }
    } finally {
        $process.WaitForExit()
        $process.Refresh()
    }

    if ($null -ne $guardFailure) {
        throw "Safety guard stopped k6: $guardFailure"
    }
    return [int] $process.ExitCode
}
