[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^chat-staging-[A-Za-z0-9-]{1,51}$')]
    [string] $RunId,
    [ValidateRange(1024, 65535)]
    [int] $LocalPort = 15433,
    [string] $AwsProfile = 'meetple-deploy',
    [string] $AwsRegion = 'ap-northeast-2',
    [switch] $AcknowledgeStagingDataDeletion
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'k6\scripts\Common.ps1')
. (Join-Path $PSScriptRoot 'k6\scripts\PostgresQps.Common.ps1')

$connection = Get-StagingPostgresConnection `
    -LocalPort $LocalPort `
    -AwsProfile $AwsProfile `
    -AwsRegion $AwsRegion
$marker = "[CHAT-LOAD:$RunId]"
$counts = Invoke-PostgresScalar -Connection $connection -Sql @"
select json_build_object(
    'messages', (select count(*) from chat_messages where content like '$marker%'),
    'outbox', (
        select count(*) from outbox_events
        where aggregate_type = 'chat_message'
          and aggregate_id in (
              select id::text from chat_messages where content like '$marker%'
          )
    )
);
"@
Write-Host "Staging run cleanup target: runId=$RunId counts=$counts"
if (-not $AcknowledgeStagingDataDeletion) {
    Write-Host 'Dry run only. Pass -AcknowledgeStagingDataDeletion to delete exactly this run.'
    exit 0
}

$sql = @"
begin;
create temporary table chat_load_message_ids on commit drop as
select id from chat_messages where content like '$marker%';

delete from outbox_events
where aggregate_type = 'chat_message'
  and aggregate_id in (select id::text from chat_load_message_ids);

delete from chat_messages where id in (select id from chat_load_message_ids);
commit;
select 'deleted';
"@
Invoke-PostgresScalar -Connection $connection -Sql $sql | Out-Null
Write-Host "Staging run deleted: $RunId"
Write-Host 'Chat read-state rows are preserved because they may predate this run.'
