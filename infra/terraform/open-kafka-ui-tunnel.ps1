param(
    [ValidateRange(1024, 65535)]
    [int]$LocalPort = 18089,

    [string]$Region = "ap-northeast-2"
)

$ErrorActionPreference = "Stop"
$TerraformDirectory = $PSScriptRoot
$Terraform = Join-Path $TerraformDirectory "..\..\build\tools\terraform\terraform.exe"

if (-not (Test-Path -LiteralPath $Terraform)) {
    throw "Terraform executable was not found: $Terraform"
}

Push-Location $TerraformDirectory
try {
    $Workspace = (& $Terraform workspace show).Trim()
    if ($Workspace -ne "staging") {
        throw "Kafka UI tunnel is allowed only from the staging workspace. Current workspace: $Workspace"
    }

    $ClusterName = (& $Terraform output -raw ecs_cluster_name).Trim()
    $ServiceName = (& $Terraform output -raw event_runtime_service_name).Trim()
    $KafkaUiUrl = (& $Terraform output -raw kafka_ui_private_url).Trim()
} finally {
    Pop-Location
}

if ([string]::IsNullOrWhiteSpace($KafkaUiUrl)) {
    throw "Kafka UI is disabled in the current Terraform state."
}

$KafkaUiHost = ([uri]$KafkaUiUrl).Host
$TaskArn = (aws ecs list-tasks --cluster $ClusterName --service-name $ServiceName --desired-status RUNNING --query "taskArns[0]" --output text --region $Region).Trim()
if ([string]::IsNullOrWhiteSpace($TaskArn) -or $TaskArn -eq "None") {
    throw "A running event runtime task was not found."
}

$ContainerInstanceArn = (aws ecs describe-tasks --cluster $ClusterName --tasks $TaskArn --query "tasks[0].containerInstanceArn" --output text --region $Region).Trim()
$InstanceId = (aws ecs describe-container-instances --cluster $ClusterName --container-instances $ContainerInstanceArn --query "containerInstances[0].ec2InstanceId" --output text --region $Region).Trim()
if ([string]::IsNullOrWhiteSpace($InstanceId) -or $InstanceId -eq "None") {
    throw "The EC2 instance hosting the event runtime was not found."
}

Write-Host "Keep this session open and browse to http://localhost:$LocalPort"
aws ssm start-session --target $InstanceId --document-name AWS-StartPortForwardingSessionToRemoteHost --parameters "host=$KafkaUiHost,portNumber=8080,localPortNumber=$LocalPort" --region $Region
