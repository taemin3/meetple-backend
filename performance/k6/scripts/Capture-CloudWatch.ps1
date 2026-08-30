[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [DateTime] $StartTime,

    [Parameter(Mandatory = $true)]
    [DateTime] $EndTime,

    [Parameter(Mandatory = $true)]
    [string] $OutputPath,

    [string] $AwsProfile = 'meetple-deploy',
    [string] $AwsRegion = 'ap-northeast-2',
    [string] $ClusterName = 'meetple-staging-cluster',
    [string] $BackendServiceName = 'meetple-staging-backend',
    [string] $EventRuntimeServiceName = 'meetple-staging-event-runtime',
    [string] $LoadBalancerName = 'meetple-staging-alb',
    [string] $TargetGroupName = 'meetple-staging-app',
    [string] $DbInstanceIdentifier = 'meetple-staging-postgres'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')

if ($EndTime -le $StartTime) {
    throw 'EndTime must be later than StartTime.'
}

$startUtc = $StartTime.ToUniversalTime()
$endUtc = $EndTime.ToUniversalTime()
$periodSeconds = if (($endUtc - $startUtc).TotalMinutes -le 30) { 60 } else { 300 }

function Get-CloudWatchSeries {
    param(
        [Parameter(Mandatory = $true)][string] $Name,
        [Parameter(Mandatory = $true)][string] $Namespace,
        [Parameter(Mandatory = $true)][string] $MetricName,
        [Parameter(Mandatory = $true)][hashtable] $Dimensions,
        [string] $Statistic = 'Average',
        [string[]] $ExtendedStatistics = @()
    )

    $arguments = @(
        'cloudwatch', 'get-metric-statistics',
        '--namespace', $Namespace,
        '--metric-name', $MetricName,
        '--start-time', $startUtc.ToString('yyyy-MM-ddTHH:mm:ssZ'),
        '--end-time', $endUtc.ToString('yyyy-MM-ddTHH:mm:ssZ'),
        '--period', [string] $periodSeconds
    )
    if ($ExtendedStatistics.Count -gt 0) {
        $arguments += '--extended-statistics'
        $arguments += $ExtendedStatistics
    } else {
        $arguments += @('--statistics', $Statistic)
    }
    $arguments += '--dimensions'
    foreach ($key in ($Dimensions.Keys | Sort-Object)) {
        $arguments += "Name=$key,Value=$($Dimensions[$key])"
    }
    $arguments += @('--profile', $AwsProfile, '--region', $AwsRegion, '--output', 'json')

    $result = Invoke-AwsJson -Arguments $arguments
    return [ordered]@{
        name = $Name
        namespace = $Namespace
        metricName = $MetricName
        statistic = if ($ExtendedStatistics.Count -gt 0) { $ExtendedStatistics } else { $Statistic }
        dimensions = $Dimensions
        datapoints = @($result.Datapoints | Sort-Object Timestamp)
    }
}

Invoke-AwsJson -Arguments @(
    'sts', 'get-caller-identity', '--profile', $AwsProfile,
    '--region', $AwsRegion, '--output', 'json'
) | Out-Null

$loadBalancer = Invoke-AwsJson -Arguments @(
    'elbv2', 'describe-load-balancers', '--names', $LoadBalancerName,
    '--profile', $AwsProfile, '--region', $AwsRegion, '--output', 'json'
)
$targetGroup = Invoke-AwsJson -Arguments @(
    'elbv2', 'describe-target-groups', '--names', $TargetGroupName,
    '--profile', $AwsProfile, '--region', $AwsRegion, '--output', 'json'
)
$containerInstances = Invoke-AwsJson -Arguments @(
    'ecs', 'list-container-instances', '--cluster', $ClusterName,
    '--status', 'ACTIVE', '--profile', $AwsProfile, '--region', $AwsRegion, '--output', 'json'
)
if (@($containerInstances.containerInstanceArns).Count -ne 1) {
    throw 'Expected exactly one active ECS container instance in staging.'
}
$containerInstance = Invoke-AwsJson -Arguments @(
    'ecs', 'describe-container-instances', '--cluster', $ClusterName,
    '--container-instances', $containerInstances.containerInstanceArns[0],
    '--profile', $AwsProfile, '--region', $AwsRegion, '--output', 'json'
)

$loadBalancerSuffix = $loadBalancer.LoadBalancers[0].LoadBalancerArn -replace '^.*:loadbalancer/', ''
$targetGroupSuffix = $targetGroup.TargetGroups[0].TargetGroupArn -replace '^.*:targetgroup/', 'targetgroup/'
$instanceId = $containerInstance.containerInstances[0].ec2InstanceId
$albDimensions = @{ LoadBalancer = $loadBalancerSuffix; TargetGroup = $targetGroupSuffix }
$backendDimensions = @{ ClusterName = $ClusterName; ServiceName = $BackendServiceName }
$eventDimensions = @{ ClusterName = $ClusterName; ServiceName = $EventRuntimeServiceName }
$ec2Dimensions = @{ InstanceId = $instanceId }
$rdsDimensions = @{ DBInstanceIdentifier = $DbInstanceIdentifier }

$metrics = @(
    Get-CloudWatchSeries -Name 'alb_request_count' -Namespace 'AWS/ApplicationELB' -MetricName 'RequestCount' -Dimensions $albDimensions -Statistic 'Sum'
    Get-CloudWatchSeries -Name 'alb_target_response_time' -Namespace 'AWS/ApplicationELB' -MetricName 'TargetResponseTime' -Dimensions $albDimensions -ExtendedStatistics @('p50', 'p95', 'p99')
    Get-CloudWatchSeries -Name 'alb_target_5xx' -Namespace 'AWS/ApplicationELB' -MetricName 'HTTPCode_Target_5XX_Count' -Dimensions $albDimensions -Statistic 'Sum'
    Get-CloudWatchSeries -Name 'backend_cpu' -Namespace 'AWS/ECS' -MetricName 'CPUUtilization' -Dimensions $backendDimensions
    Get-CloudWatchSeries -Name 'backend_memory' -Namespace 'AWS/ECS' -MetricName 'MemoryUtilization' -Dimensions $backendDimensions
    Get-CloudWatchSeries -Name 'backend_running_tasks' -Namespace 'ECS/ContainerInsights' -MetricName 'RunningTaskCount' -Dimensions $backendDimensions -Statistic 'Minimum'
    Get-CloudWatchSeries -Name 'event_runtime_cpu' -Namespace 'AWS/ECS' -MetricName 'CPUUtilization' -Dimensions $eventDimensions
    Get-CloudWatchSeries -Name 'event_runtime_memory' -Namespace 'AWS/ECS' -MetricName 'MemoryUtilization' -Dimensions $eventDimensions
    Get-CloudWatchSeries -Name 'event_runtime_running_tasks' -Namespace 'ECS/ContainerInsights' -MetricName 'RunningTaskCount' -Dimensions $eventDimensions -Statistic 'Minimum'
    Get-CloudWatchSeries -Name 'ec2_cpu' -Namespace 'AWS/EC2' -MetricName 'CPUUtilization' -Dimensions $ec2Dimensions
    Get-CloudWatchSeries -Name 'ec2_cpu_credit_balance' -Namespace 'AWS/EC2' -MetricName 'CPUCreditBalance' -Dimensions $ec2Dimensions -Statistic 'Minimum'
    Get-CloudWatchSeries -Name 'ecs_cluster_memory_utilized' -Namespace 'ECS/ContainerInsights' -MetricName 'MemoryUtilized' -Dimensions @{ ClusterName = $ClusterName }
    Get-CloudWatchSeries -Name 'ecs_cluster_memory_reserved' -Namespace 'ECS/ContainerInsights' -MetricName 'MemoryReserved' -Dimensions @{ ClusterName = $ClusterName }
    Get-CloudWatchSeries -Name 'rds_cpu' -Namespace 'AWS/RDS' -MetricName 'CPUUtilization' -Dimensions $rdsDimensions
    Get-CloudWatchSeries -Name 'rds_cpu_credit_balance' -Namespace 'AWS/RDS' -MetricName 'CPUCreditBalance' -Dimensions $rdsDimensions -Statistic 'Minimum'
    Get-CloudWatchSeries -Name 'rds_connections' -Namespace 'AWS/RDS' -MetricName 'DatabaseConnections' -Dimensions $rdsDimensions -Statistic 'Maximum'
    Get-CloudWatchSeries -Name 'rds_freeable_memory' -Namespace 'AWS/RDS' -MetricName 'FreeableMemory' -Dimensions $rdsDimensions -Statistic 'Minimum'
    Get-CloudWatchSeries -Name 'rds_read_latency' -Namespace 'AWS/RDS' -MetricName 'ReadLatency' -Dimensions $rdsDimensions
    Get-CloudWatchSeries -Name 'rds_write_latency' -Namespace 'AWS/RDS' -MetricName 'WriteLatency' -Dimensions $rdsDimensions
    Get-CloudWatchSeries -Name 'rds_free_storage' -Namespace 'AWS/RDS' -MetricName 'FreeStorageSpace' -Dimensions $rdsDimensions -Statistic 'Minimum'
    Get-CloudWatchSeries -Name 'rds_oldest_slot_lag' -Namespace 'AWS/RDS' -MetricName 'OldestReplicationSlotLag' -Dimensions $rdsDimensions -Statistic 'Maximum'
    Get-CloudWatchSeries -Name 'rds_replication_slot_disk_usage' -Namespace 'AWS/RDS' -MetricName 'ReplicationSlotDiskUsage' -Dimensions $rdsDimensions -Statistic 'Maximum'
    Get-CloudWatchSeries -Name 'rds_transaction_logs_disk_usage' -Namespace 'AWS/RDS' -MetricName 'TransactionLogsDiskUsage' -Dimensions $rdsDimensions -Statistic 'Maximum'
)

$document = [ordered]@{
    capturedAtUtc = [DateTime]::UtcNow.ToString('o')
    startTimeUtc = $startUtc.ToString('o')
    endTimeUtc = $endUtc.ToString('o')
    periodSeconds = $periodSeconds
    instanceId = $instanceId
    notes = @(
        'EC2 OS memory is not available because CloudWatch Agent is not installed.',
        'ECS cluster MemoryUtilized and MemoryReserved are included as the current memory proxy.'
    )
    metrics = $metrics
}

$parent = Split-Path -Parent $OutputPath
if (-not [string]::IsNullOrWhiteSpace($parent)) {
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
}
$document | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $OutputPath -Encoding utf8
Write-Host "CloudWatch metrics written to: $OutputPath"
