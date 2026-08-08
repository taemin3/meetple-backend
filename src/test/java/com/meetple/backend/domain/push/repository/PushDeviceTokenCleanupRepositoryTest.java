package com.meetple.backend.domain.push.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meetple.backend.domain.push.fcm.InvalidPushTarget;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PushDeviceTokenCleanupRepositoryTest {

    @Test
    void deletesTokenOnlyWhenIdAndSentTokenHashStillMatch() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:push-token-cleanup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS push_device_tokens");
        jdbcTemplate.execute("""
                CREATE TABLE push_device_tokens (
                    id BIGINT PRIMARY KEY,
                    token_hash VARCHAR(64) NOT NULL
                )
                """);
        jdbcTemplate.update(
                "INSERT INTO push_device_tokens (id, token_hash) VALUES (?, ?)",
                10L,
                "refreshed-hash"
        );
        PushDeviceTokenCleanupRepository repository =
                new PushDeviceTokenCleanupRepository(jdbcTemplate);

        repository.deleteAllMatching(List.of(new InvalidPushTarget(10L, "sent-old-hash")));

        assertThat(countTokens(jdbcTemplate)).isEqualTo(1);

        repository.deleteAllMatching(List.of(new InvalidPushTarget(10L, "refreshed-hash")));

        assertThat(countTokens(jdbcTemplate)).isZero();
    }

    private Integer countTokens(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM push_device_tokens",
                Integer.class
        );
    }
}
