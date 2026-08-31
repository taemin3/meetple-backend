[CmdletBinding()]
param(
    [ValidateRange(1024, 65535)]
    [int] $LocalPort = 15433,
    [switch] $DryRun,
    [string] $AwsProfile = 'meetple-deploy',
    [string] $AwsRegion = 'ap-northeast-2',
    [string] $ClusterName = 'meetple-staging-cluster',
    [string] $DbInstanceIdentifier = 'meetple-staging-postgres'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')
. (Join-Path $PSScriptRoot 'PostgresQps.Common.ps1')

if ($null -eq (Get-Command session-manager-plugin -ErrorAction SilentlyContinue)) {
    throw @'
AWS Session Manager plugin is required.
Install it from the official AWS Windows installer, open a new PowerShell window, and run: session-manager-plugin
'@
}
if (Test-TcpPort -HostName '127.0.0.1' -Port $LocalPort) {
    throw "Local port $LocalPort is already in use. Choose another -LocalPort."
}

$containerInstances = Invoke-AwsJson -Arguments @(
    'ecs', 'list-container-instances', '--cluster', $ClusterName, '--status', 'ACTIVE',
    '--profile', $AwsProfile, '--region', $AwsRegion, '--output', 'json'
)
if (@($containerInstances.containerInstanceArns).Count -eq 0) {
    throw 'No ACTIVE ECS container instance was found.'
}
$containerInstanceArn = [string] $containerInstances.containerInstanceArns[0]

$describedInstances = Invoke-AwsJson -Arguments @(
    'ecs', 'describe-container-instances', '--cluster', $ClusterName,
    '--container-instances', $containerInstanceArn,
    '--profile', $AwsProfile, '--region', $AwsRegion, '--output', 'json'
)
$instanceId = [string] $describedInstances.containerInstances[0].ec2InstanceId
if ([string]::IsNullOrWhiteSpace($instanceId)) {
    throw 'The ECS container instance does not have an EC2 instance ID.'
}

$managedInstances = Invoke-AwsJson -Arguments @(
    'ssm', 'describe-instance-information',
    '--filters', "Key=InstanceIds,Values=$instanceId",
    '--profile', $AwsProfile, '--region', $AwsRegion, '--output', 'json'
)
if (@($managedInstances.InstanceInformationList).Count -eq 0 -or
    $managedInstances.InstanceInformationList[0].PingStatus -cne 'Online') {
    throw "EC2 instance $instanceId is not Online in Systems Manager."
}

$db = Invoke-AwsJson -Arguments @(
    'rds', 'describe-db-instances', '--db-instance-identifier', $DbInstanceIdentifier,
    '--profile', $AwsProfile, '--region', $AwsRegion, '--output', 'json'
)
$remoteHost = [string] $db.DBInstances[0].Endpoint.Address
$remotePort = [string] $db.DBInstances[0].Endpoint.Port

if ($DryRun) {
    Write-Host "Tunnel validation succeeded: 127.0.0.1:$LocalPort -> private staging PostgreSQL:$remotePort through $instanceId."
    Write-Host 'No SSM session was opened.'
    return
}

Write-Host "Opening an encrypted SSM tunnel: 127.0.0.1:$LocalPort -> staging PostgreSQL:$remotePort"
Write-Host 'Keep this PowerShell window open. Use Ctrl+C here only after the isolated test and QPS capture finish.'

$sessionInputPath = Join-Path ([System.IO.Path]::GetTempPath()) ("meetple-ssm-{0}.json" -f [Guid]::NewGuid().ToString('N'))
try {
    $sessionInput = [ordered]@{
        Target = $instanceId
        DocumentName = 'AWS-StartPortForwardingSessionToRemoteHost'
        Parameters = [ordered]@{
            host = @($remoteHost)
            portNumber = @($remotePort)
            localPortNumber = @([string] $LocalPort)
        }
    } | ConvertTo-Json -Depth 4
    [System.IO.File]::WriteAllText($sessionInputPath, $sessionInput, [System.Text.UTF8Encoding]::new($false))
    $sessionInputUri = 'file://{0}' -f $sessionInputPath.Replace('\', '/')

    & aws ssm start-session `
        --cli-input-json $sessionInputUri `
        --profile $AwsProfile `
        --region $AwsRegion
    if ($LASTEXITCODE -ne 0) {
        throw "SSM port-forwarding session failed with exit code $LASTEXITCODE."
    }
} finally {
    Remove-Item -LiteralPath $sessionInputPath -Force -ErrorAction SilentlyContinue
}
