package com.meetple.backend.domain.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.health.registry.ReactiveHealthContributorRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class HealthEndpointConfigurationTest {

    @Autowired
    private HealthEndpointGroups healthEndpointGroups;

    @Autowired
    private HealthContributorRegistry healthContributorRegistry;

    @Autowired
    private ReactiveHealthContributorRegistry reactiveHealthContributorRegistry;

    @Test
    void livenessDoesNotDependOnExternalServices() {
        HealthEndpointGroup liveness = healthEndpointGroups.get("liveness");

        assertThat(liveness).isNotNull();
        assertThat(liveness.isMember("livenessState")).isTrue();
        assertThat(liveness.isMember("db")).isFalse();
        assertThat(liveness.isMember("redis")).isFalse();
        assertThat(liveness.isMember("kafka")).isFalse();
    }

    @Test
    void readinessDependsOnDatabaseAndRedisButNotKafka() {
        HealthEndpointGroup readiness = healthEndpointGroups.get("readiness");

        assertThat(readiness).isNotNull();
        assertThat(readiness.isMember("readinessState")).isTrue();
        assertThat(readiness.isMember("db")).isTrue();
        assertThat(readiness.isMember("redis")).isTrue();
        assertThat(readiness.isMember("kafka")).isFalse();
        assertThat(healthContributorRegistry.getContributor("db")).isNotNull();
        assertThat(reactiveHealthContributorRegistry.getContributor("redis")).isNotNull();
    }
}
