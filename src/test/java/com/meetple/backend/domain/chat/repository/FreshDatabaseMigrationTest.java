package com.meetple.backend.domain.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class FreshDatabaseMigrationTest {

    @Test
    void migrationsCreateACompleteSchemaOnAnEmptyDatabase() throws Exception {
        String url = "jdbc:h2:mem:fresh-database-migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway flyway = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load();

        var result = flyway.migrate();

        assertThat(result.migrationsExecuted).isEqualTo(13);
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            assertThat(connection.getMetaData()
                    .getTables(null, null, "MEMBERS", new String[]{"TABLE"})
                    .next()).isTrue();
            assertThat(connection.getMetaData()
                    .getTables(null, null, "MEETINGS", new String[]{"TABLE"})
                    .next()).isTrue();
            try (var categories = statement.executeQuery("select count(*) from categories")) {
                assertThat(categories.next()).isTrue();
                assertThat(categories.getInt(1)).isEqualTo(3);
            }
        }
    }
}
