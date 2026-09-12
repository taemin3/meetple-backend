[CmdletBinding()]
param(
    [string] $OutputPath = '.\performance\results\chat-locks.jsonl',
    [ValidateRange(0.05, 5.0)]
    [double] $IntervalSeconds = 0.1
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$outputDirectory = Split-Path -Parent $OutputPath
if (-not [string]::IsNullOrWhiteSpace($outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}

$sql = @"
\pset tuples_only on
\pset format unaligned
select coalesce(json_agg(row_to_json(sample)), '[]'::json)
from (
    select
        clock_timestamp() as observed_at,
        activity.pid,
        activity.state,
        activity.wait_event_type,
        activity.wait_event,
        pg_blocking_pids(activity.pid) as blocking_pids,
        extract(milliseconds from clock_timestamp() - activity.query_start)::bigint
            as query_age_ms,
        left(regexp_replace(activity.query, '[[:space:]]+', ' ', 'g'), 240) as query
    from pg_stat_activity activity
    where activity.datname = current_database()
      and activity.pid <> pg_backend_pid()
      and (
          activity.wait_event_type = 'Lock'
          or cardinality(pg_blocking_pids(activity.pid)) > 0
      )
    order by activity.pid
) sample;
\watch $IntervalSeconds
"@

Write-Host "PostgreSQL lock sampling: every $IntervalSeconds second(s)"
Write-Host "Output: $OutputPath"
Write-Host 'Stop with Ctrl+C immediately after the load condition finishes.'

$sql |
    docker compose exec -T postgres sh -lc 'psql -X -q -U "$POSTGRES_USER" -d "$POSTGRES_DB"' |
    Where-Object { $_ -match '^\s*\[' } |
    Tee-Object -FilePath $OutputPath
