package com.meetple.backend.global.observability;

import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CloudWatchApplicationMetricFilterTest {

    @Test
    void allowsSelectedInfrastructureAndRedisMeters() {
        SimpleMeterRegistry registry = registryWithFilter();

        registry.gauge("hikaricp.connections.pending", 2);
        Timer.builder("lettuce.command.completion")
                .tag("command", "MGET")
                .register(registry);

        assertThat(registry.find("hikaricp.connections.pending").gauge()).isNotNull();
        assertThat(registry.find("lettuce.command.completion").timer()).isNotNull();
    }

    @Test
    void allowsOnlyPerformanceTestHttpUris() {
        SimpleMeterRegistry registry = registryWithFilter();

        Timer.builder("http.server.requests")
                .tag("uri", "/api/v1/meetings")
                .register(registry);
        Timer.builder("http.server.requests")
                .tag("uri", "/api/v1/meetings/summaries")
                .register(registry);
        Timer.builder("http.server.requests")
                .tag("uri", "/api/v1/performance/auth-probe")
                .register(registry);
        Timer.builder("http.server.requests")
                .tag("uri", "/api/v1/auth/login")
                .register(registry);

        assertThat(registry.find("http.server.requests")
                .tag("uri", "/api/v1/meetings")
                .timer()).isNotNull();
        assertThat(registry.find("http.server.requests")
                .tag("uri", "/api/v1/meetings/summaries")
                .timer()).isNotNull();
        assertThat(registry.find("http.server.requests")
                .tag("uri", "/api/v1/performance/auth-probe")
                .timer()).isNotNull();
        assertThat(registry.find("http.server.requests")
                .tag("uri", "/api/v1/auth/login")
                .timer()).isNull();
    }

    @Test
    void deniesUnselectedMeters() {
        SimpleMeterRegistry registry = registryWithFilter();

        registry.counter("logback.events", "level", "error").increment();

        assertThat(registry.find("logback.events").counter()).isNull();
    }

    @Test
    void configuresNamespaceCommonTagsAndAllowlistOnCloudWatchRegistry() {
        CloudWatchApplicationMetricsProperties properties = new CloudWatchApplicationMetricsProperties(
                true,
                "Meetple/Test/Application",
                Duration.ofHours(1),
                "test"
        );
        CloudWatchApplicationMetricsConfiguration configuration = new CloudWatchApplicationMetricsConfiguration();
        CloudWatchConfig config = configuration.applicationMetricsCloudWatchConfig(properties);
        CloudWatchAsyncClient cloudWatchClient = mock(CloudWatchAsyncClient.class);

        CloudWatchMeterRegistry registry = configuration.applicationMetricsCloudWatchRegistry(
                config,
                cloudWatchClient,
                properties
        );
        try {
            registry.gauge("tomcat.threads.busy", 3);
            registry.counter("logback.events").increment();

            assertThat(config.namespace()).isEqualTo("Meetple/Test/Application");
            assertThat(config.step()).isEqualTo(Duration.ofHours(1));
            assertThat(registry.find("tomcat.threads.busy")
                    .tags("service", "backend", "environment", "test")
                    .gauge()).isNotNull();
            assertThat(registry.find("logback.events").counter()).isNull();
        } finally {
            registry.close();
        }
    }

    private SimpleMeterRegistry registryWithFilter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(new CloudWatchApplicationMetricFilter());
        return registry;
    }
}
