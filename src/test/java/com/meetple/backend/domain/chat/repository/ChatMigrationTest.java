package com.meetple.backend.domain.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class ChatMigrationTest {

    @Test
    void migrationAdoptsExistingSchemaAndCreatesChatTables() throws Exception {
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

        assertThat(result.migrationsExecuted).isEqualTo(1);
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(tableExists(connection, "CHAT_MESSAGES")).isTrue();
            assertThat(tableExists(connection, "CHAT_READ_STATES")).isTrue();
        }
    }

    private boolean tableExists(java.sql.Connection connection, String tableName) throws Exception {
        try (var tables = connection.getMetaData().getTables(null, null, tableName, new String[]{"TABLE"})) {
            return tables.next();
        }
    }
}
