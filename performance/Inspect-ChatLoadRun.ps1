[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9-]{1,64}$')]
    [string] $RunId
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$sql = @"
\pset format aligned
\pset tuples_only off
select
    cm.id as chat_message_id,
    cm.meeting_id,
    cm.sender_id,
    cm.room_sequence,
    cm.client_message_id,
    cm.created_at
from chat_messages cm
where cm.content like '[CHAT-LOAD:$RunId]%'
order by cm.meeting_id, cm.room_sequence;

select
    oe.id as outbox_event_id,
    oe.aggregate_id as chat_message_id,
    oe.topic,
    oe.occurred_at
from outbox_events oe
where oe.aggregate_type = 'chat_message'
  and oe.aggregate_id in (
      select cm.id::text
      from chat_messages cm
      where cm.content like '[CHAT-LOAD:$RunId]%'
  )
order by oe.occurred_at, oe.id;
"@

Write-Host "Read-only inspection for runId=$RunId"
$sql | docker compose exec -T postgres sh -lc 'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
if ($LASTEXITCODE -ne 0) {
    throw "PostgreSQL inspection failed with exit code $LASTEXITCODE."
}
