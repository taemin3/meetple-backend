[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9-]{3,64}$')]
    [string] $DatasetId,
    [switch] $AcknowledgeLocalDataDeletion
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$emailPrefix = "chat-load-$DatasetId-"
$titlePrefix = "[CHAT-LOAD-DATASET:$DatasetId]"

if (-not $AcknowledgeLocalDataDeletion) {
    Write-Host "Dataset: $DatasetId"
    Write-Host "Members: email LIKE '$emailPrefix%@example.invalid'"
    Write-Host "Meetings: title LIKE '$titlePrefix%'"
    Write-Host 'Dry run only. Pass -AcknowledgeLocalDataDeletion to delete this fixture.'
    exit 0
}

$sql = @"
begin;
create temporary table fixture_meeting_ids on commit drop as
select id
from meetings
where title like '$titlePrefix%';

create temporary table fixture_message_ids on commit drop as
select id
from chat_messages
where meeting_id in (select id from fixture_meeting_ids);

delete from outbox_events
where aggregate_type = 'chat_message'
  and aggregate_id in (select id::text from fixture_message_ids);

delete from meetings
where id in (select id from fixture_meeting_ids);

delete from members
where email like '$emailPrefix%@example.invalid';
commit;
"@

Write-Host "Deleting only synthetic rows for dataset $DatasetId."
$sql | docker compose exec -T postgres `
    sh -lc 'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
if ($LASTEXITCODE -ne 0) {
    throw "Fixture cleanup failed with exit code $LASTEXITCODE."
}

Write-Host "Fixture deleted: $DatasetId"
