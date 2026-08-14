package com.meetple.backend.domain.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.Types;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class ChatMigrationTest {

    @Test
    void migrationsAdoptExistingSchemaAndCreateManagedTables() throws Exception {
        String url = "jdbc:h2:mem:chat-migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("create table members (id bigint primary key)");
            statement.execute("""
                    create table meetings (
                        id bigint primary key,
                        meeting_date timestamp not null,
                        end_date timestamp null
                    )
                    """);
            statement.execute("""
                    insert into meetings (id, meeting_date, end_date)
                    values
                        (1, timestamp '2026-08-20 14:00:00', null),
                        (2, timestamp '2026-08-20 14:00:00', timestamp '2026-08-20 18:00:00')
                    """);
            statement.execute("""
                    create table meeting_images (
                        id bigint primary key,
                        meeting_id bigint not null,
                        image_url varchar(2048) not null,
                        sort_order integer not null
                    )
                    """);
        }

        Flyway flyway = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
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
}
