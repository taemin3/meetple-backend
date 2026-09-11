package com.meetple.backend.global.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meetple.backend.domain.category.entity.Category;
import com.meetple.backend.domain.category.repository.CategoryRepository;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.MeetingStatus;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
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
            .parse("meetple-postgres:16-3.5-bigm")
            .asCompatibleSubstituteFor("postgres");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE)
            .withDatabaseName("meetple")
            .withUsername("meetple")
            .withPassword("meetple")
            .withCommand(
                    "postgres",
                    "-c",
                    "shared_preload_libraries=pg_bigm,pg_stat_statements"
            );

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

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void applicationStartsAfterFlywayCreatesSchemaOnEmptyPostgresql() {
        assertThat(entityManagerFactory.isOpen()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE",
                Integer.class
        )).isEqualTo(19);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT version
                FROM legal_documents
                WHERE type = 'PRIVACY_POLICY'
                ORDER BY effective_at DESC, id DESC
                LIMIT 1
                """,
                String.class
        )).isEqualTo("2026-09-12.1");
    }

    @Test
    @Transactional
    void postgisNearbyQueryUsesGeneratedLocationAndFiltersRows() {
        Member host = memberRepository.save(Member.createUser(
                "postgis-nearby@meetple.test",
                "encoded-password",
                "postgis-host",
                "Seoul"
        ));
        Category category = categoryRepository.save(Category.create("postgis-test"));

        Meeting nearby = meetingRepository.save(meeting(
                host,
                category,
                "Nearby meeting",
                "37.566500",
                "126.978000"
        ));
        Meeting secondNearby = meetingRepository.save(meeting(
                host,
                category,
                "Second nearby meeting",
                "37.568000",
                "126.978000"
        ));
        meetingRepository.save(meeting(
                host,
                category,
                "Far meeting",
                "37.610000",
                "126.978000"
        ));
        Meeting deleted = meetingRepository.save(meeting(
                host,
                category,
                "Deleted nearby meeting",
                "37.566500",
                "126.978000"
        ));
        deleted.softDelete(LocalDateTime.now());
        meetingRepository.flush();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT ST_AsText(location::geometry) FROM meetings WHERE id = ?",
                String.class,
                nearby.getId()
        )).isEqualTo("POINT(126.978 37.5665)");

        Slice<Meeting> firstSlice = meetingRepository.findNearbyMeetings(
                MeetingStatus.RECRUITING.name(),
                "postgis-test",
                37.5665,
                126.9780,
                1000,
                PageRequest.of(0, 1)
        );

        assertThat(firstSlice.getContent()).hasSize(1);
        assertThat(firstSlice.hasNext()).isTrue();
        assertThat(firstSlice.getContent()).extracting(Meeting::getId)
                .containsExactly(nearby.getId());

        Slice<Meeting> secondSlice = meetingRepository.findNearbyMeetings(
                MeetingStatus.RECRUITING.name(),
                "postgis-test",
                37.5665,
                126.9780,
                1000,
                PageRequest.of(1, 1)
        );

        assertThat(secondSlice.getContent()).hasSize(1);
        assertThat(secondSlice.hasNext()).isFalse();
        assertThat(secondSlice.getContent()).extracting(Meeting::getId)
                .containsExactly(secondNearby.getId());
    }

    @Test
    @Transactional
    void postgisSearchQueryReturnsGlobalKeywordMatchesInDistanceOrder() {
        Member host = memberRepository.save(Member.createUser(
                "postgis-search@meetple.test",
                "encoded-password",
                "search-host",
                "Seoul"
        ));
        Category exercise = categoryRepository.save(Category.create("postgis-search-exercise"));
        Category study = categoryRepository.save(Category.create("postgis-search-study"));
        Meeting nearby = meetingRepository.save(meeting(
                host,
                exercise,
                "Nearby 100% running",
                "37.521900",
                "126.924500"
        ));
        Meeting farAway = meetingRepository.save(meeting(
                host,
                exercise,
                "Busan 100% running",
                "35.179600",
                "129.075600"
        ));
        meetingRepository.save(meeting(
                host,
                exercise,
                "Nearby 1000 running",
                "37.520000",
                "126.924500"
        ));
        meetingRepository.save(meeting(
                host,
                study,
                "Closer 100% running study",
                "37.521000",
                "126.924500"
        ));
        Meeting completed = meetingRepository.save(meeting(
                host,
                exercise,
                "Completed 100% running",
                "37.521500",
                "126.924500"
        ));
        completed.complete();
        Meeting deleted = meetingRepository.save(meeting(
                host,
                exercise,
                "Deleted 100% running",
                "37.521600",
                "126.924500"
        ));
        deleted.softDelete(LocalDateTime.now());
        meetingRepository.flush();

        Page<Long> result = meetingRepository.searchMeetingIds(
                MeetingStatus.RECRUITING.name(),
                "%100!% running%",
                "postgis-search-exercise",
                37.5219,
                126.9245,
                PageRequest.of(0, 20)
        );

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).containsExactly(nearby.getId(), farAway.getId());
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

    private Meeting meeting(
            Member host,
            Category category,
            String title,
            String latitude,
            String longitude
    ) {
        return Meeting.create(
                host,
                category,
                title,
                "PostGIS integration test meeting",
                "Test location",
                "Seoul",
                new BigDecimal(latitude),
                new BigDecimal(longitude),
                10,
                LocalDateTime.now().plusDays(1),
                null
        );
    }
}
