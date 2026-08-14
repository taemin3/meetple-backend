package com.meetple.backend.domain.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.Types;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class ChatMigrationTest {

    @Test
    void migrationsAdoptExistingSchemaAndCreateManagedTables() throws Exception {
        String url = "jdbc:h2:mem:chat-migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("""
                    create table members (
                        id bigint primary key,
                        profile_image_url varchar(255)
                    )
                    """);
            statement.execute("""
                    create table meetings (
                        id bigint primary key,
                        meeting_date timestamp not null,
                        end_date timestamp null,
                        thumbnail_image_url varchar(2048)
                    )
                    """);
            statement.execute("""
                    insert into members (id, profile_image_url)
                    values (1, 'https://cdn.meetple.com/custom-assets/profile/1/550e8400-e29b-41d4-a716-446655440000.png')
                    """);
            statement.execute("""
                    insert into meetings (id, meeting_date, end_date, thumbnail_image_url)
                    values
                        (1, timestamp '2026-08-20 14:00:00', null,
                            'https://cdn.meetple.com/custom-assets/meeting/1/550e8400-e29b-41d4-a716-446655440001.png'),
                        (2, timestamp '2026-08-20 14:00:00', timestamp '2026-08-20 18:00:00', null)
                    """);
            statement.execute("""
                    create table meeting_images (
                        id bigint primary key,
                        meeting_id bigint not null,
                        image_url varchar(2048) not null,
                        sort_order integer not null
                    )
                    """);
            statement.execute("""
                    insert into meeting_images (id, meeting_id, image_url, sort_order)
                    values
                        (
                            1,
                            1,
                            'https://cdn.meetple.com/custom-assets/meeting/1/550e8400-e29b-41d4-a716-446655440001.png',
                            0
                        ),
                        (2, 2, 'https://example.com/legacy-image.png', 0)
                    """);
        }

        Flyway flyway = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .placeholders(Map.of("imageStorageKeyPrefix", "custom-assets"))
                .load();

        var result = flyway.migrate();

        assertThat(result.migrationsExecuted).isEqualTo(7);
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(tableExists(connection, "CHAT_MESSAGES")).isTrue();
            assertThat(tableExists(connection, "CHAT_READ_STATES")).isTrue();
            assertThat(tableExists(connection, "OUTBOX_EVENTS")).isTrue();
            assertThat(tableExists(connection, "PUSH_DEVICE_TOKENS")).isTrue();
            assertThat(tableExists(connection, "PUSH_EVENT_DELIVERIES")).isTrue();
            assertThat(tableExists(connection, "CHAT_NOTIFICATION_SETTINGS")).isTrue();
            assertThat(columnDataType(connection, "PUSH_DEVICE_TOKENS", "TOKEN_HASH"))
                    .isEqualTo(Types.VARCHAR);
            assertThat(columnDataType(connection, "MEMBERS", "PROFILE_IMAGE_OBJECT_KEY"))
                    .isEqualTo(Types.VARCHAR);
            assertThat(columnDataType(connection, "MEETING_IMAGES", "OBJECT_KEY"))
                    .isEqualTo(Types.VARCHAR);
            assertThat(singleStringValue(
                    connection,
                    "select profile_image_object_key from members where id = 1"
            )).isEqualTo("custom-assets/profile/1/550e8400-e29b-41d4-a716-446655440000.png");
            assertThat(singleStringValue(
                    connection,
                    "select thumbnail_image_object_key from meetings where id = 1"
            )).isEqualTo("custom-assets/meeting/1/550e8400-e29b-41d4-a716-446655440001.png");
            assertThat(singleStringValue(
                    connection,
                    "select object_key from meeting_images where id = 1"
            )).isEqualTo("custom-assets/meeting/1/550e8400-e29b-41d4-a716-446655440001.png");
            assertThat(singleStringValue(
                    connection,
                    "select object_key from meeting_images where id = 2"
            )).isNull();
            assertThat(columnIsNullable(connection, "MEETING_IMAGES", "OBJECT_KEY"))
                    .isTrue();
            assertThat(columnTypeName(connection, "PUSH_EVENT_DELIVERIES", "CLAIM_ID"))
                    .isEqualTo("UUID");
            assertThat(columnDataType(connection, "PUSH_EVENT_DELIVERIES", "CLAIMED_UNTIL"))
                    .isEqualTo(Types.TIMESTAMP);
            try (var statement = connection.createStatement();
                 var resultSet = statement.executeQuery("select end_date from meetings where id = 1")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getTimestamp("end_date").toLocalDateTime())
                        .isEqualTo(java.time.LocalDateTime.of(2026, 8, 20, 16, 0));
            }
            try (var statement = connection.createStatement();
                 var resultSet = statement.executeQuery("select end_date from meetings where id = 2")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getTimestamp("end_date").toLocalDateTime())
                        .isEqualTo(java.time.LocalDateTime.of(2026, 8, 20, 18, 0));
            }
        }
    }

    private boolean tableExists(java.sql.Connection connection, String tableName) throws Exception {
        try (var tables = connection.getMetaData().getTables(null, null, tableName, new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    private int columnDataType(
            java.sql.Connection connection,
            String tableName,
            String columnName
    ) throws Exception {
        try (var columns = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            assertThat(columns.next()).isTrue();
            return columns.getInt("DATA_TYPE");
        }
    }

    private String columnTypeName(
            java.sql.Connection connection,
            String tableName,
            String columnName
    ) throws Exception {
        try (var columns = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            assertThat(columns.next()).isTrue();
            return columns.getString("TYPE_NAME");
        }
    }

    private boolean columnIsNullable(
            java.sql.Connection connection,
            String tableName,
            String columnName
    ) throws Exception {
        try (var columns = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            assertThat(columns.next()).isTrue();
            return columns.getInt("NULLABLE") == java.sql.DatabaseMetaData.columnNullable;
        }
    }

    private String singleStringValue(java.sql.Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }
}
