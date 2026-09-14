[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9-]{3,48}$')]
    [string] $DatasetId,
    [Parameter(Mandatory = $true)]
    [string] $ManifestPath,
    [string] $BaseUrl = 'https://api.meetple.shop',
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^api\.meetple\.shop$')]
    [string] $ConfirmTarget,
    [ValidateRange(1024, 65535)]
    [int] $LocalPort = 15433,
    [string] $AwsProfile = 'meetple-deploy',
    [string] $AwsRegion = 'ap-northeast-2',
    [switch] $AcknowledgeStagingDataCreation,
    [switch] $ValidateLocalHashing,
    [switch] $DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'k6\scripts\Common.ps1')
. (Join-Path $PSScriptRoot 'k6\scripts\PostgresQps.Common.ps1')

$target = [Uri]$BaseUrl
if ($target.Scheme -cne 'https' -or $target.Host -cne 'api.meetple.shop' -or
        $ConfirmTarget -cne $target.Host) {
    throw 'Fixture creation only allows the confirmed staging target https://api.meetple.shop.'
}

Write-Host "Dataset: $DatasetId"
Write-Host "Target API: $($target.GetLeftPart([UriPartial]::Authority))"
Write-Host "Target DB: meetple-staging-postgres through 127.0.0.1:$LocalPort"
Write-Host 'Creates: up to 1 dedicated category, 10 members, 10 meetings, 90 APPROVED participation rows'
Write-Host 'Push devices/messages/read states/Outbox rows: 0'
Write-Host 'Existing application rows are not updated or deleted.'
Write-Host "Manifest: $ManifestPath (contains a generated password; do not commit)"
if ($DryRun) {
    Write-Host 'Dry run complete. No database row or manifest was created.'
    exit 0
}
if (-not $AcknowledgeStagingDataCreation -and -not $ValidateLocalHashing) {
    throw 'Pass -AcknowledgeStagingDataCreation after reviewing the printed staging mutation plan.'
}

$randomBytes = [byte[]]::new(24)
$randomNumberGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $randomNumberGenerator.GetBytes($randomBytes)
} finally {
    $randomNumberGenerator.Dispose()
}
$password = 'Aa1!' + [Convert]::ToBase64String($randomBytes).Replace('+', 'x').Replace('/', 'y')
$escapedPassword = $password.Replace("'", "''")

# Generate the BCrypt hash locally with the Java/Spring Security dependency that
# this repository already uses. No Docker daemon or staging DB extension is needed.
$java = Get-Command java -ErrorAction SilentlyContinue
$javac = Get-Command javac -ErrorAction SilentlyContinue
if ($null -eq $java -or $null -eq $javac) {
    throw 'Java 21 JDK commands java and javac are required.'
}
$userProfileDirectory = if (-not [string]::IsNullOrWhiteSpace($env:USERPROFILE)) {
    $env:USERPROFILE
} else {
    [Environment]::GetFolderPath([Environment+SpecialFolder]::UserProfile)
}
$gradleUserHome = if (-not [string]::IsNullOrWhiteSpace($env:GRADLE_USER_HOME)) {
    $env:GRADLE_USER_HOME
} else {
    Join-Path $userProfileDirectory '.gradle'
}
$gradleModuleCache = Join-Path $gradleUserHome 'caches\modules-2\files-2.1'
$cryptoJar = Get-ChildItem `
    (Join-Path $gradleModuleCache 'org.springframework.security\spring-security-crypto') `
    -Recurse -Filter 'spring-security-crypto-*.jar' -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1
$loggingJar = Get-ChildItem `
    (Join-Path $gradleModuleCache 'commons-logging\commons-logging') `
    -Recurse -Filter 'commons-logging-*.jar' -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1
if ($null -eq $cryptoJar -or $null -eq $loggingJar) {
    throw 'Spring Security jars were not found in the Gradle cache. Run .\gradlew.bat test once, then retry.'
}

$hashDirectory = Join-Path ([IO.Path]::GetTempPath()) `
    ("meetple-chat-bcrypt-{0}" -f [Guid]::NewGuid().ToString('N'))
$hashSourcePath = Join-Path $hashDirectory 'ChatFixturePasswordHash.java'
$hashClassPath = '{0};{1}' -f $cryptoJar.FullName, $loggingJar.FullName
$previousFixturePassword = $env:MEETPLE_CHAT_FIXTURE_PASSWORD
try {
    New-Item -ItemType Directory -Path $hashDirectory | Out-Null
    [IO.File]::WriteAllText(
        $hashSourcePath,
        @'
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class ChatFixturePasswordHash {
    public static void main(String[] args) {
        String password = System.getenv("MEETPLE_CHAT_FIXTURE_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Fixture password is missing");
        }
        System.out.println(new BCryptPasswordEncoder(10).encode(password));
    }
}
'@,
        [Text.UTF8Encoding]::new($false)
    )
    & $javac.Source -cp $hashClassPath $hashSourcePath
    if ($LASTEXITCODE -ne 0) {
        throw "Fixture password helper compilation failed with exit code $LASTEXITCODE."
    }
    $env:MEETPLE_CHAT_FIXTURE_PASSWORD = $password
    $passwordHashOutput = @(
        & $java.Source -cp "$hashDirectory;$hashClassPath" ChatFixturePasswordHash
    )
    if ($LASTEXITCODE -ne 0) {
        throw "Fixture password hashing failed with exit code $LASTEXITCODE."
    }
    $passwordHash = [string]($passwordHashOutput | Select-Object -Last 1)
    if ([string]::IsNullOrWhiteSpace($passwordHash) -or -not $passwordHash.StartsWith('$2')) {
        throw 'Spring Security did not return a BCrypt password hash.'
    }
} finally {
    $env:MEETPLE_CHAT_FIXTURE_PASSWORD = $previousFixturePassword
    Remove-Item -LiteralPath $hashSourcePath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath (Join-Path $hashDirectory 'ChatFixturePasswordHash.class') `
        -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $hashDirectory -Force -ErrorAction SilentlyContinue
}
if ($ValidateLocalHashing) {
    Write-Host 'Java/Spring Security BCrypt prerequisite validation succeeded.'
    Write-Host 'No staging database connection was opened and no manifest was created.'
    exit 0
}

$connection = Get-StagingPostgresConnection `
    -LocalPort $LocalPort `
    -AwsProfile $AwsProfile `
    -AwsRegion $AwsRegion
$escapedPasswordHash = $passwordHash.Replace("'", "''")
$escapedDatasetId = $DatasetId.Replace("'", "''")
$emailPrefix = "chat-load-$DatasetId-"
$titlePrefix = "[CHAT-LOAD-DATASET:$DatasetId]"

$sql = @"
begin;
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
    '$escapedPasswordHash',
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
select row_number() over (order by email)::integer as fixture_index, id, email
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
    'Synthetic fixture for staging chat load measurement',
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
select row_number() over (order by title)::integer as fixture_index, id
from meetings
where title like '$titlePrefix%';

do `$sequence`$
begin
    if to_regclass('public.chat_room_sequences') is not null then
        insert into chat_room_sequences (meeting_id, last_sequence)
        select id, 0 from fixture_meetings;
    end if;
end
`$sequence`$;

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
    'targetEnvironment', 'staging',
    'fixtureKind', 'chat-load-v1',
    'pushDeviceCount', 0,
    'baseUrl', '$($target.GetLeftPart([UriPartial]::Authority))',
    'clients', (
        select json_agg(
            json_build_object('email', email, 'password', '$escapedPassword')
            order by fixture_index
        ) from fixture_members
    ),
    'roomIds', (
        select json_agg(id::text order by fixture_index)
        from fixture_meetings
    )
);
commit;
"@

$manifestJson = Invoke-PostgresScalar -Connection $connection -Sql $sql
$manifest = $manifestJson | ConvertFrom-Json
$manifestDirectory = Split-Path -Parent $ManifestPath
if (-not [string]::IsNullOrWhiteSpace($manifestDirectory)) {
    New-Item -ItemType Directory -Path $manifestDirectory -Force | Out-Null
}
[IO.File]::WriteAllText(
    $ManifestPath,
    ($manifest | ConvertTo-Json -Depth 5),
    [Text.UTF8Encoding]::new($false)
)

Write-Host 'Staging fixture created successfully.'
Write-Host "Manifest: $ManifestPath"
Write-Host 'Keep the manifest outside Git because it contains the generated password.'
