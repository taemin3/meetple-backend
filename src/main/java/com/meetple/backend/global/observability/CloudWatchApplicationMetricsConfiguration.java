package com.meetple.backend.global.observability;

import io.lettuce.core.metrics.MicrometerCommandLatencyRecorder;
import io.lettuce.core.metrics.MicrometerOptions;
import io.lettuce.core.protocol.CommandType;
import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.ClientResourcesBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "meetple.observability.cloudwatch",
        name = "enabled",
        havingValue = "true"
)
class CloudWatchApplicationMetricsConfiguration {

    private static final int MAXIMUM_EXPORTED_METERS = 100;

    @Bean(destroyMethod = "close")
    CloudWatchAsyncClient applicationMetricsCloudWatchClient() {
        return CloudWatchAsyncClient.create();
    }

    @Bean
    CloudWatchConfig applicationMetricsCloudWatchConfig(CloudWatchApplicationMetricsProperties properties) {
        return new CloudWatchConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String namespace() {
                return properties.namespace();
            }

            @Override
            public java.time.Duration step() {
                return properties.step();
            }
        };
    }

    @Bean
    CloudWatchMeterRegistry applicationMetricsCloudWatchRegistry(
            CloudWatchConfig config,
            CloudWatchAsyncClient cloudWatchClient,
            CloudWatchApplicationMetricsProperties properties
    ) {
        CloudWatchMeterRegistry registry = new CloudWatchMeterRegistry(config, Clock.SYSTEM, cloudWatchClient);
        registry.config()
                .commonTags(
                        "service", "backend",
                        "environment", properties.environment()
                )
                .meterFilter(MeterFilter.ignoreTags(
                        "action",
                        "cause",
                        "exception",
                        "local",
                        "method",
                        "outcome",
                        "remote",
                        "status"
                ))
                .meterFilter(new CloudWatchApplicationMetricFilter())
                .meterFilter(MeterFilter.maximumAllowableMetrics(MAXIMUM_EXPORTED_METERS));
        return registry;
    }

    @Bean
    ClientResourcesBuilderCustomizer lettuceCommandLatencyMetrics(MeterRegistry meterRegistry) {
        MicrometerOptions options = MicrometerOptions.builder()
                .enabledCommands(CommandType.GET, CommandType.MGET, CommandType.SET, CommandType.DEL)
                .histogram(false)
                .localDistinction(false)
                .build();
        MicrometerCommandLatencyRecorder recorder = new MicrometerCommandLatencyRecorder(meterRegistry, options);
        return builder -> builder.commandLatencyRecorder(recorder);
    }
}
