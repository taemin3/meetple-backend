package com.meetple.backend.global.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Testcontainers(disabledWithoutDocker = true)
class FreshDatabaseApplicationContextTest {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName
            .parse("meetple-postgres:16-3.4-bigm")
            .asCompatibleSubstituteFor("postgres");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE)
            .withDatabaseName("meetple")
            .withUsername("meetple")
            .withPassword("meetple");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> "1");
        registry.add("spring.data.redis.password", () -> "test-redis-password");
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.username", () -> "test-mail-user");
        registry.add("spring.mail.password", () -> "test-mail-password");
        registry.add("auth.email-verification.hmac-secret", () -> "test-email-verification-secret-1234567890");
        registry.add("auth.email-verification.from-address", () -> "noreply@meetple.test");
        registry.add("naver.location.search-client-id", () -> "test-search-client");
        registry.add("naver.location.search-client-secret", () -> "test-search-secret");
        registry.add("naver.location.maps-client-id", () -> "test-maps-client");
        registry.add("naver.location.maps-client-secret", () -> "test-maps-secret");
        registry.add("jwt.secret", () -> "test-jwt-secret-key-for-meetple-backend-1234567890");
        registry.add("auth.email-delivery.kafka.consumer-enabled", () -> "false");
        registry.add("image.deletion.kafka.consumer-enabled", () -> "false");
        registry.add("push.kafka.consumer-enabled", () -> "false");
        registry.add("push.fcm.enabled", () -> "false");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("chat.session-invalidation.redis-enabled", () -> "false");
        registry.add("chat.message-fan-out.redis-enabled", () -> "false");
    }

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void applicationStartsAfterFlywayCreatesSchemaOnEmptyPostgresql() {
        assertThat(entityManagerFactory.isOpen()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE",
                Integer.class
        )).isEqualTo(15);
    }

    @Test
    void productionProbesSeparateProcessHealthFromRequiredDependencies() throws Exception {
        mockMvc.perform(get("/livez"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/readyz"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }

    @Test
    void productionDoesNotExposeExampleHealthOrApiDocumentationEndpoints() throws Exception {
        for (String path : List.of(
                "/health",
                "/health-data",
                "/health-error",
                "/health-notfound",
                "/swagger-ui.html",
                "/v3/api-docs"
        )) {
            mockMvc.perform(get(path))
                    .andExpect(status().isNotFound());
        }
    }
}
