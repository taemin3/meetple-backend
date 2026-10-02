package com.meetple.backend.global.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class FreshDatabaseMigrationTest {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName
            .parse("meetple-postgres:16-3.5-bigm-vector0.8.6")
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

    @Test
    void migrationsCreateCompleteSchemaOnEmptyPostgresql() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        var firstMigration = flyway.migrate();

        assertThat(firstMigration.migrationsExecuted).isEqualTo(31);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();

        try (var connection = openConnection()) {
            assertThat(applicationTables(connection)).contains(
                    "categories",
                    "members",
                    "meetings",
                    "meeting_participations",
                    "meeting_bookmarks",
                    "meeting_images",
                    "notifications",
                    "chat_messages",
                    "chat_read_states",
                    "chat_room_sequences",
                    "outbox_events",
                    "push_device_tokens",
                    "push_event_deliveries",
                    "chat_notification_settings",
                    "legal_documents",
                    "member_legal_records",
                    "debezium_heartbeat",
                    "reports",
                    "member_blocks",
                    "meeting_embeddings",
                    "moderation_policies",
                    "moderation_policy_chunks",
                    "moderation_policy_embeddings",
                    "report_analyses",
                    "report_analysis_policies",
                    "report_warnings",
                    "moderation_actions",
                    "moderation_policy_audits"
            );
            assertThat(appliedMigrationVersions(connection)).containsExactly(
                    "0.1", "1", "2", "3", "4", "5", "6",
                    "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19",
                    "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30"
            );
            assertThat(categoryNames(connection)).containsExactlyInAnyOrder(
                    "운동", "스터디", "취미", "친목", "여행", "맛집", "비즈니스", "반려동물"
            );
            assertThat(rowCount(connection, "legal_documents")).isEqualTo(5);
            assertThat(privacyPolicyVersions(connection))
                    .containsExactly("2026-08-22", "2026-09-12", "2026-09-12.1");
            assertThat(latestPrivacyPolicyContent(connection))
                    .contains(
                            "장소 검색어",
                            "채팅 메시지 식별자",
                            "최종 스냅샷",
                            "meetple99@gmail.com"
                    );
            assertThat(rowCount(connection, "debezium_heartbeat")).isEqualTo(1);
            assertThat(rowCount(connection, "moderation_policies")).isEqualTo(6);
            assertThat(rowCount(connection, "moderation_policy_chunks")).isEqualTo(8);

            assertThat(columnType(connection, "outbox_events", "payload")).isEqualTo("jsonb");
            assertThat(columnType(connection, "outbox_events", "id")).isEqualTo("uuid");
            assertThat(columnType(connection, "push_event_deliveries", "claim_id")).isEqualTo("uuid");
            assertThat(columnType(connection, "meetings", "location")).isEqualTo("geography");
            assertThat(columnType(connection, "meeting_embeddings", "embedding")).isEqualTo("vector");
            assertThat(formattedColumnType(connection, "meeting_embeddings", "embedding"))
                    .isEqualTo("vector(1536)");
            assertThat(formattedColumnType(
                    connection,
                    "moderation_policy_embeddings",
                    "embedding"
            )).isEqualTo("vector(1536)");
            assertThat(columnGeneration(connection, "meetings", "location")).isEqualTo("ALWAYS");
            assertThat(indexDefinition(connection, "idx_meetings_location_gist"))
                    .contains("USING gist (location)");
            assertThat(indexDefinition(connection, "idx_meeting_embeddings_embedding_hnsw"))
                    .contains("USING hnsw (embedding vector_cosine_ops)");
            assertThat(indexDefinition(
                    connection,
                    "idx_moderation_policy_embeddings_embedding_hnsw"
            )).contains("USING hnsw (embedding vector_cosine_ops)");
            assertThat(installedExtensions(connection)).contains("postgis", "pg_bigm", "vector");
            assertThat(columnIsNullable(connection, "members", "email_verified_at")).isTrue();
            assertThat(columnIsNullable(connection, "members", "profile_image_object_key")).isTrue();
            assertThat(columnIsNullable(connection, "members", "deleted_at")).isTrue();
            assertThat(columnIsNullable(connection, "meetings", "deleted_at")).isTrue();
            assertThat(columnIsNullable(connection, "meetings", "thumbnail_image_object_key")).isTrue();
            assertThat(columnIsNullable(connection, "meeting_images", "image_url")).isTrue();
            assertThat(columnIsNullable(connection, "meeting_images", "object_key")).isTrue();
            assertThat(columnIsNullable(connection, "reports", "target_snapshot")).isTrue();
            assertThat(columnIsNullable(connection, "reports", "target_snapshot_hash")).isTrue();
            assertThat(columnIsNullable(connection, "members", "suspended_until")).isTrue();
            assertThat(columnIsNullable(connection, "members", "permanently_suspended_at")).isTrue();
            assertThat(columnIsNullable(connection, "members", "suspension_report_id")).isTrue();
            assertThat(columnIsNullable(
                    connection,
                    "meetings",
                    "moderation_deleted_by_report_id"
            )).isTrue();

            assertThat(uniqueConstraints(connection, "categories")).contains("uk_categories_name");
            assertThat(uniqueConstraints(connection, "members")).contains("uk_members_email");
            assertThat(uniqueConstraints(connection, "meeting_participations"))
                    .contains("uk_meeting_participations_meeting_member");
            assertThat(uniqueConstraints(connection, "meeting_bookmarks"))
                    .contains("uk_meeting_bookmarks_meeting_member");
            assertThat(uniqueConstraints(connection, "member_blocks"))
                    .contains("uk_member_blocks_relationship");
            assertThat(uniqueConstraints(connection, "moderation_policies"))
                    .contains("uk_moderation_policies_code_version");
            assertThat(uniqueConstraints(connection, "moderation_policy_chunks"))
                    .contains(
                            "uk_moderation_policy_chunks_clause",
                            "uk_moderation_policy_chunks_order"
                    );
            assertThat(uniqueConstraints(connection, "chat_messages"))
                    .contains("uk_chat_messages_room_sequence", "uk_chat_messages_client_message");
        }

        assertThat(flyway.migrate().migrationsExecuted).isZero();

        verifyPolicyManagementUpgrade(flyway);
    }

    private void verifyPolicyManagementUpgrade(Flyway flyway) throws Exception {
        flyway.clean();
        Flyway beforePolicyManagement = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("28"))
                .load();
        beforePolicyManagement.migrate();

        try (var connection = openConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO moderation_policies (
                        policy_code, title, policy_type, target_type,
                        effective_from, effective_to, active, version, created_at, updated_at
                    ) VALUES
                        ('LEGACY-SPAM', '기존 정책 1', 'SPAM', 'ALL',
                         DATE '2026-09-01', NULL, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                        ('LEGACY-SPAM', '기존 정책 2', 'SPAM', 'ALL',
                         DATE '2026-10-01', NULL, TRUE, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                        ('COMMUNITY-SPAM', '사용자 정의 스팸 정책', 'SPAM', 'ALL',
                         DATE '2026-09-01', NULL, FALSE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            statement.executeUpdate("""
                    INSERT INTO moderation_policy_chunks (
                        policy_id, clause_code, chunk_order, content, content_hash,
                        created_at, updated_at
                    )
                    SELECT id, 'CUSTOM-1', 0, '기존 사용자 정의 조항',
                           'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                           CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    FROM moderation_policies
                    WHERE policy_code = 'COMMUNITY-SPAM' AND version = 1
                    """);
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (var connection = openConnection()) {
            assertThat(activePolicyVersions(connection, "LEGACY-SPAM")).containsExactly(2);
            assertThat(policyChunkContents(connection, "COMMUNITY-SPAM", 1))
                    .containsExactly("기존 사용자 정의 조항");
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
    }

    private Set<String> applicationTables(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                """);
             var resultSet = statement.executeQuery()) {
            var tables = new java.util.HashSet<String>();
            while (resultSet.next()) {
                tables.add(resultSet.getString("table_name"));
            }
            return tables;
        }
    }

    private List<String> appliedMigrationVersions(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT version
                FROM flyway_schema_history
                WHERE success = TRUE
                ORDER BY installed_rank
                """);
             var resultSet = statement.executeQuery()) {
            var versions = new ArrayList<String>();
            while (resultSet.next()) {
                versions.add(resultSet.getString("version"));
            }
            return versions;
        }
    }

    private List<String> categoryNames(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT name FROM categories");
             var resultSet = statement.executeQuery()) {
            var names = new ArrayList<String>();
            while (resultSet.next()) {
                names.add(resultSet.getString("name"));
            }
            return names;
        }
    }

    private List<Integer> activePolicyVersions(Connection connection, String policyCode)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT version
                FROM moderation_policies
                WHERE policy_code = ? AND active = TRUE
                ORDER BY version
                """)) {
            statement.setString(1, policyCode);
            try (var resultSet = statement.executeQuery()) {
                var versions = new ArrayList<Integer>();
                while (resultSet.next()) {
                    versions.add(resultSet.getInt("version"));
                }
                return versions;
            }
        }
    }

    private List<String> policyChunkContents(
            Connection connection,
            String policyCode,
            int version
    ) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT chunk.content
                FROM moderation_policy_chunks chunk
                JOIN moderation_policies policy ON policy.id = chunk.policy_id
                WHERE policy.policy_code = ? AND policy.version = ?
                ORDER BY chunk.chunk_order
                """)) {
            statement.setString(1, policyCode);
            statement.setInt(2, version);
            try (var resultSet = statement.executeQuery()) {
                var contents = new ArrayList<String>();
                while (resultSet.next()) {
                    contents.add(resultSet.getString("content"));
                }
                return contents;
            }
        }
    }

    private List<String> privacyPolicyVersions(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT version
                FROM legal_documents
                WHERE type = 'PRIVACY_POLICY'
                ORDER BY effective_at, id
                """);
             var resultSet = statement.executeQuery()) {
            var versions = new ArrayList<String>();
            while (resultSet.next()) {
                versions.add(resultSet.getString("version"));
            }
            return versions;
        }
    }

    private String latestPrivacyPolicyContent(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT content
                FROM legal_documents
                WHERE type = 'PRIVACY_POLICY'
                ORDER BY effective_at DESC, id DESC
                LIMIT 1
                """);
             var resultSet = statement.executeQuery()) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString("content");
        }
    }

    private int rowCount(Connection connection, String tableName) throws SQLException {
        if (!Set.of(
                "legal_documents",
                "debezium_heartbeat",
                "moderation_policies",
                "moderation_policy_chunks"
        ).contains(tableName)) {
            throw new IllegalArgumentException("Unsupported table: " + tableName);
        }
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }

    private String columnType(Connection connection, String tableName, String columnName)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT udt_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
                """)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (var resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString("udt_name");
            }
        }
    }

    private boolean columnIsNullable(Connection connection, String tableName, String columnName)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
                """)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (var resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return "YES".equals(resultSet.getString("is_nullable"));
            }
        }
    }

    private String formattedColumnType(Connection connection, String tableName, String columnName)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT format_type(attribute.atttypid, attribute.atttypmod)
                FROM pg_attribute attribute
                JOIN pg_class relation ON relation.oid = attribute.attrelid
                JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                WHERE namespace.nspname = 'public'
                  AND relation.relname = ?
                  AND attribute.attname = ?
                  AND attribute.attnum > 0
                  AND NOT attribute.attisdropped
                """)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (var resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    private Set<String> installedExtensions(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT extname FROM pg_extension");
             var resultSet = statement.executeQuery()) {
            var extensions = new java.util.HashSet<String>();
            while (resultSet.next()) {
                extensions.add(resultSet.getString("extname"));
            }
            return extensions;
        }
    }

    private String columnGeneration(Connection connection, String tableName, String columnName)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT is_generated
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
                """)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (var resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString("is_generated");
            }
        }
    }

    private String indexDefinition(Connection connection, String indexName) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname = ?
                """)) {
            statement.setString(1, indexName);
            try (var resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString("indexdef");
            }
        }
    }

    private Set<String> uniqueConstraints(Connection connection, String tableName)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND constraint_type = 'UNIQUE'
                """)) {
            statement.setString(1, tableName);
            try (var resultSet = statement.executeQuery()) {
                var constraints = new java.util.HashSet<String>();
                while (resultSet.next()) {
                    constraints.add(resultSet.getString("constraint_name"));
                }
                return constraints;
            }
        }
    }
}
