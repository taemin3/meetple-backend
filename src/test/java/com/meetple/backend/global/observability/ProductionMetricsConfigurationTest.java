package com.meetple.backend.global.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class ProductionMetricsConfigurationTest {

    @Test
    void disablesAutomaticRepositoryMetrics() throws IOException {
        PropertySource<?> properties = new YamlPropertySourceLoader()
                .load("application-prod", new ClassPathResource("application-prod.yml"))
                .getFirst();

        assertThat(properties.getProperty("management.metrics.data.repository.autotime.enabled"))
                .isEqualTo(false);
    }
}
