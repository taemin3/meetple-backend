package com.meetple.backend.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class AiMeetingSearchRepositoryTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName
            .parse("meetple-postgres:16-3.5-bigm").asCompatibleSubstituteFor("postgres"))
            .withCommand("postgres", "-c", "shared_preload_libraries=pg_bigm,pg_stat_statements");
    private JdbcTemplate jdbc;
    private AiMeetingSearchRepository repository;
    private final LocalDateTime start = LocalDateTime.of(2026, 10, 3, 0, 0);

    @BeforeEach void prepare() {
        var datasource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(datasource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(datasource);
        repository = new AiMeetingSearchRepository(new NamedParameterJdbcTemplate(datasource));
        jdbc.execute("TRUNCATE meetings, members CASCADE");
        jdbc.execute("""
                INSERT INTO members (id,email,password,nickname,role,created_at,updated_at)
                VALUES (1,'viewer@example.test','test','viewer','USER',now(),now()),
                       (2,'host@example.test','test','host','USER',now(),now()),
                       (3,'blocked@example.test','test','blocked','USER',now(),now())
                """);
    }

    private void insert(long id, String title, long host, String status, int people, double latitude, LocalDateTime date) {
        jdbc.update("""
                INSERT INTO meetings (id,title,content,location_name,address,latitude,longitude,
                  max_people,current_people,meeting_date,status,host_id,category_id,created_at,updated_at)
                VALUES (?,?,'처음 달리는 분 환영','가상 공원','가상 주소',?,127,10,?,?,?, ?,
                  (SELECT id FROM categories WHERE name='운동'),now(),now())
                """, id, title, latitude, people, date, status, host);
    }

    private AiSearchContracts.Filters filters(String keyword) {
        return new AiSearchContracts.Filters(keyword, "운동", start, start.plusDays(2), 37.5, 127, 3000);
    }

    @Test void excludesBlockedDeletedFullOutOfRangeAndPastMeetings() {
        insert(10, "러닝", 2, "RECRUITING", 2, 37.5, start.plusHours(15));
        insert(11, "러닝", 3, "RECRUITING", 2, 37.5, start.plusHours(15));
        insert(12, "러닝", 2, "RECRUITING", 2, 37.5, start.plusHours(15));
        insert(13, "러닝", 2, "FULL", 10, 37.5, start.plusHours(15));
        insert(14, "러닝", 2, "RECRUITING", 10, 37.5, start.plusHours(15));
        insert(15, "러닝", 2, "RECRUITING", 2, 35, start.plusHours(15));
        insert(16, "러닝", 2, "RECRUITING", 2, 37.5, start.minusHours(1));
        insert(17, "러닝", 2, "RECRUITING", 2, 37.5, start.plusDays(2));
        jdbc.execute("UPDATE meetings SET deleted_at=now() WHERE id=12");
        jdbc.execute("INSERT INTO member_blocks (blocker_member_id,blocked_member_id,created_at,updated_at) VALUES (1,3,now(),now())");
        assertThat(repository.search(1, filters("러닝")).items()).extracting(AiSearchContracts.Candidate::id)
                .containsExactly(10L);
        jdbc.execute("UPDATE members SET deleted_at=now() WHERE id=1");
        assertThat(repository.search(1, filters("러닝")).items()).isEmpty();
    }

    @Test void treatsSqlWildcardsAsLiteralUserText() {
        insert(10, "100%_러닝!", 2, "RECRUITING", 2, 37.5, start.plusHours(15));
        insert(11, "100XX러닝", 2, "RECRUITING", 2, 37.5, start.plusHours(15));
        assertThat(repository.search(1, filters("%_러닝!")).items()).extracting(AiSearchContracts.Candidate::id)
                .containsExactly(10L);
        assertThat(repository.search(1, filters("' OR 1=1 --")).items()).isEmpty();
    }

    @Test void returnsBoundedCandidatesAndMoreFlag() {
        for (long id = 1; id <= 21; id++) insert(id, "러닝", 2, "RECRUITING", 2, 37.5, start.plusHours(15));
        var result = repository.search(1, filters("러닝"));
        assertThat(result.items()).hasSize(20);
        assertThat(result.hasMore()).isTrue();
        assertThat(result.items().getFirst().id()).isEqualTo(1L);
    }
}
