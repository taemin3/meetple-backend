[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9-]{3,48}$')]
    [string] $DatasetId,
    [Parameter(Mandatory = $true)]
    [string] $ManifestPath,
    [string] $BaseUrl = 'http://127.0.0.1:8080',
    [switch] $AcknowledgeLocalDataCreation,
    [switch] $DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$target = [Uri]$BaseUrl
if ($target.Scheme -notin @('http', 'https') -or
        $target.Host -notin @('127.0.0.1', 'localhost', '::1')) {
    throw 'Chat fixture creation is restricted to a loopback backend URL.'
}
if (-not $DryRun -and -not $AcknowledgeLocalDataCreation) {
    throw 'Pass -AcknowledgeLocalDataCreation after reviewing the printed mutation plan.'
}

Write-Host "Dataset: $DatasetId"
Write-Host 'Target database: the PostgreSQL service from this repository docker-compose.yml'
Write-Host 'Creates: 10 members, 10 meetings, 90 APPROVED participation rows'
Write-Host 'Push devices/messages/read states/Outbox rows: 0'
Write-Host "Manifest: $ManifestPath (contains the generated test password; do not commit)"
if ($DryRun) {
    Write-Host 'Dry run complete. No database row or file was created.'
    exit 0
}

$randomBytes = [byte[]]::new(24)
[Security.Cryptography.RandomNumberGenerator]::Fill($randomBytes)
$password = 'Aa1!' + [Convert]::ToBase64String($randomBytes).Replace('+', 'x').Replace('/', 'y')
$escapedPassword = $password.Replace("'", "''")
$escapedDatasetId = $DatasetId.Replace("'", "''")
$emailPrefix = "chat-load-$DatasetId-"
$titlePrefix = "[CHAT-LOAD-DATASET:$DatasetId]"

$sql = @"
begin;
create extension if not exists pgcrypto;

do `$validation`$
begin
    if exists (select 1 from members where email like '$emailPrefix%@example.invalid') then
        raise exception 'Fixture members already exist for dataset $escapedDatasetId.';
    end if;
    if exists (select 1 from meetings where title like '$titlePrefix%') then
        raise exception 'Fixture meetings already exist for dataset $escapedDatasetId.';
    end if;
end
`$validation`$;

insert into categories (name, default_image_url, created_at, updated_at)
select 'CHAT_LOAD_TEST', null, clock_timestamp(), clock_timestamp()
where not exists (select 1 from categories where name = 'CHAT_LOAD_TEST');

insert into members (
    email, password, email_verified_at, nickname, introduction, region, role,
    profile_image_object_key, deleted_at, created_at, updated_at
)
select
    '$emailPrefix' || lpad(index::text, 2, '0') || '@example.invalid',
    crypt('$escapedPassword', gen_salt('bf', 10)),
    clock_timestamp(),
    'chat-load-' || lpad(index::text, 2, '0'),
    'synthetic chat load fixture',
    '서울',
    'USER',
    null,
    null,
    clock_timestamp(),
    clock_timestamp()
from generate_series(1, 10) as index;

create temporary table fixture_members on commit drop as
select
    row_number() over (order by email)::integer as fixture_index,
    id,
    email
from members
where email like '$emailPrefix%@example.invalid';

insert into meetings (
    title, content, location_name, address, latitude, longitude,
    max_people, current_people, meeting_date, end_date, cancel_reason,
    deleted_at, status, thumbnail_image_object_key, host_id, category_id,
    created_at, updated_at
)
select
    '$titlePrefix room-' || lpad(index::text, 2, '0'),
    'Synthetic fixture for chat lock contention measurement',
    '채팅 부하 테스트 장소',
    '서울특별시 중구 세종대로 110',
    37.566500,
    126.978000,
    20,
    10,
    clock_timestamp() + interval '30 days',
    null,
    null,
    null,
    'RECRUITING',
    null,
    (select id from fixture_members where fixture_index = 1),
    (select id from categories where name = 'CHAT_LOAD_TEST'),
    clock_timestamp(),
    clock_timestamp()
from generate_series(1, 10) as index;

create temporary table fixture_meetings on commit drop as
select
    row_number() over (order by title)::integer as fixture_index,
    id
from meetings
where title like '$titlePrefix%';

insert into meeting_participations (
    status, message, reviewed_at, canceled_at,
    meeting_id, member_id, created_at, updated_at
)
select
    'APPROVED',
    'synthetic chat load fixture',
    clock_timestamp(),
    null,
    meeting.id,
    member.id,
    clock_timestamp(),
    clock_timestamp()
from fixture_meetings meeting
cross join fixture_members member
where member.fixture_index > 1;

select json_build_object(
    'datasetId', '$escapedDatasetId',
    'baseUrl', '$($target.GetLeftPart([UriPartial]::Authority))',
    'clients', (
        select json_agg(
            json_build_object('email', email, 'password', '$escapedPassword')
            order by fixture_index
        )
        from fixture_members
    ),
    'roomIds', (
        select json_agg(id::text order by fixture_index)
        from fixture_meetings
    )
);
commit;
"@

$rawOutput = $sql | docker-compose.exe exec -T postgres `
    sh -lc 'psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
if ($LASTEXITCODE -ne 0) {
    throw "Fixture creation failed with exit code $LASTEXITCODE."
}
$jsonLine = @($rawOutput | Where-Object { $_ -match '^\s*\{' }) | Select-Object -Last 1
if ([string]::IsNullOrWhiteSpace($jsonLine)) {
    throw 'Fixture creation did not return a manifest.'
}

$manifest = $jsonLine | ConvertFrom-Json
$manifestDirectory = Split-Path -Parent $ManifestPath
if (-not [string]::IsNullOrWhiteSpace($manifestDirectory)) {
    New-Item -ItemType Directory -Path $manifestDirectory -Force | Out-Null
}
$manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $ManifestPath -Encoding utf8

Write-Host 'Fixture created successfully.'
Write-Host "Manifest: $ManifestPath"
Write-Host 'Keep this file outside Git because it contains the generated test password.'
