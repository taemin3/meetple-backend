[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9-]{3,48}$')]
    [string] $DatasetId,
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
$emailPrefix = "chat-load-$DatasetId-"
$titlePrefix = "[CHAT-LOAD-DATASET:$DatasetId]"

$counts = Invoke-PostgresScalar -Connection $connection -Sql @"
select json_build_object(
    'members', (select count(*) from members where email like '$emailPrefix%@example.invalid'),
    'meetings', (select count(*) from meetings where title like '$titlePrefix%'),
    'messages', (
        select count(*) from chat_messages
        where meeting_id in (select id from meetings where title like '$titlePrefix%')
    )
);
"@
Write-Host "Staging fixture cleanup target: dataset=$DatasetId counts=$counts"
if (-not $AcknowledgeStagingDataDeletion) {
    Write-Host 'Dry run only. Pass -AcknowledgeStagingDataDeletion to delete exactly this fixture.'
    exit 0
}

$sql = @"
begin;
create temporary table fixture_meeting_ids on commit drop as
select id from meetings where title like '$titlePrefix%';

create temporary table fixture_message_ids on commit drop as
select id from chat_messages where meeting_id in (select id from fixture_meeting_ids);

delete from outbox_events
where aggregate_type = 'chat_message'
  and aggregate_id in (select id::text from fixture_message_ids);

delete from meetings where id in (select id from fixture_meeting_ids);
delete from members where email like '$emailPrefix%@example.invalid';
delete from categories
where name = 'CHAT_LOAD_TEST'
  and not exists (select 1 from meetings where category_id = categories.id);
commit;
select 'deleted';
"@
Invoke-PostgresScalar -Connection $connection -Sql $sql | Out-Null
Write-Host "Staging fixture deleted: $DatasetId"
