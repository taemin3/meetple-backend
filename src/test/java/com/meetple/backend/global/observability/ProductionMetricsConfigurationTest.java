package com.meetple.backend.global.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class ProductionMetricsConfigurationTest {

    private PropertySource<?> loadProductionProperties() throws IOException {
        return new YamlPropertySourceLoader()
                .load("application-prod", new ClassPathResource("application-prod.yml"))
                .getFirst();
    }

    @Test
    void disablesAutomaticRepositoryMetrics() throws IOException {
        PropertySource<?> properties = loadProductionProperties();

        assertThat(properties.getProperty("management.metrics.data.repository.autotime.enabled"))
                .isEqualTo(false);
    }

    @Test
    void keepsTheMeasuredHikariDefaultAtTenConnections() throws IOException {
        PropertySource<?> properties = loadProductionProperties();

        assertThat(properties.getProperty("spring.datasource.hikari.maximum-pool-size"))
                .isEqualTo("${SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE:10}");
    }
}
