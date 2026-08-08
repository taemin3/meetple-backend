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
            statement.execute("create table meetings (id bigint primary key)");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();

        var result = flyway.migrate();

        assertThat(result.migrationsExecuted).isEqualTo(4);
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(tableExists(connection, "CHAT_MESSAGES")).isTrue();
            assertThat(tableExists(connection, "CHAT_READ_STATES")).isTrue();
            assertThat(tableExists(connection, "OUTBOX_EVENTS")).isTrue();
            assertThat(tableExists(connection, "PUSH_DEVICE_TOKENS")).isTrue();
            assertThat(tableExists(connection, "PUSH_EVENT_DELIVERIES")).isTrue();
            assertThat(columnDataType(connection, "PUSH_DEVICE_TOKENS", "TOKEN_HASH"))
                    .isEqualTo(Types.VARCHAR);
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
}
