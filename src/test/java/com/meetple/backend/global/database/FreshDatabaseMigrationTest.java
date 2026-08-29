package com.meetple.backend.global.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class FreshDatabaseMigrationTest {

    private static final DockerImageName POSTGIS_IMAGE = DockerImageName
            .parse("postgis/postgis:16-3.4")
            .asCompatibleSubstituteFor("postgres");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGIS_IMAGE)
            .withDatabaseName("meetple")
            .withUsername("meetple")
            .withPassword("meetple");

    @Test
    void migrationsCreateCompleteSchemaOnEmptyPostgresql() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();

        var firstMigration = flyway.migrate();

        assertThat(firstMigration.migrationsExecuted).isEqualTo(14);
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
                    "outbox_events",
                    "push_device_tokens",
                    "push_event_deliveries",
                    "chat_notification_settings",
                    "legal_documents",
                    "member_legal_records",
                    "debezium_heartbeat"
            );
            assertThat(appliedMigrationVersions(connection)).containsExactly(
                    "0.1", "1", "2", "3", "4", "5", "6",
                    "7", "8", "9", "10", "11", "12", "13"
            );
            assertThat(categoryNames(connection)).containsExactlyInAnyOrder("운동", "스터디", "취미");
            assertThat(rowCount(connection, "legal_documents")).isEqualTo(3);
            assertThat(rowCount(connection, "debezium_heartbeat")).isEqualTo(1);

            assertThat(columnType(connection, "outbox_events", "payload")).isEqualTo("jsonb");
            assertThat(columnType(connection, "outbox_events", "id")).isEqualTo("uuid");
            assertThat(columnType(connection, "push_event_deliveries", "claim_id")).isEqualTo("uuid");
            assertThat(columnIsNullable(connection, "members", "email_verified_at")).isTrue();
            assertThat(columnIsNullable(connection, "members", "profile_image_object_key")).isTrue();
            assertThat(columnIsNullable(connection, "meetings", "deleted_at")).isTrue();
            assertThat(columnIsNullable(connection, "meetings", "thumbnail_image_object_key")).isTrue();
            assertThat(columnIsNullable(connection, "meeting_images", "image_url")).isTrue();
            assertThat(columnIsNullable(connection, "meeting_images", "object_key")).isTrue();

            assertThat(uniqueConstraints(connection, "categories")).contains("uk_categories_name");
            assertThat(uniqueConstraints(connection, "members")).contains("uk_members_email");
            assertThat(uniqueConstraints(connection, "meeting_participations"))
                    .contains("uk_meeting_participations_meeting_member");
            assertThat(uniqueConstraints(connection, "meeting_bookmarks"))
                    .contains("uk_meeting_bookmarks_meeting_member");
            assertThat(uniqueConstraints(connection, "chat_messages"))
                    .contains("uk_chat_messages_room_sequence", "uk_chat_messages_client_message");
        }

        assertThat(flyway.migrate().migrationsExecuted).isZero();
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

    private int rowCount(Connection connection, String tableName) throws SQLException {
        if (!Set.of("legal_documents", "debezium_heartbeat").contains(tableName)) {
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
