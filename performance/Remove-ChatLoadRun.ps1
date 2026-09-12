[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9-]{1,64}$')]
    [string] $RunId,
    [switch] $AcknowledgeSyntheticDataDeletion
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $AcknowledgeSyntheticDataDeletion) {
    throw 'Inspect the run first, then pass -AcknowledgeSyntheticDataDeletion.'
}

$sql = @"
begin;
create temporary table chat_load_message_ids on commit drop as
select id
from chat_messages
where content like '[CHAT-LOAD:$RunId]%';

delete from outbox_events
where aggregate_type = 'chat_message'
  and aggregate_id in (select id::text from chat_load_message_ids);

delete from chat_messages
where id in (select id from chat_load_message_ids);
commit;
"@

Write-Host "Deleting only messages marked [CHAT-LOAD:$RunId] and their Outbox rows."
Write-Host 'Chat read-state rows are preserved because they may predate this run.'
$sql | docker compose exec -T postgres sh -lc 'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
if ($LASTEXITCODE -ne 0) {
    throw "PostgreSQL cleanup failed with exit code $LASTEXITCODE."
}
