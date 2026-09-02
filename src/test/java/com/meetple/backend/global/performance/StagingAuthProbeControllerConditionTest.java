package com.meetple.backend.global.performance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;

class StagingAuthProbeControllerConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(StagingAuthProbeController.class);

    @Test
    void probeIsDisabledByDefault() {
        contextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(StagingAuthProbeController.class));
    }

    @Test
    void probeReturnsNoContentWhenExplicitlyEnabled() {
        contextRunner
                .withPropertyValues("meetple.performance.auth-probe.enabled=true")
                .run(context -> {
                    StagingAuthProbeController controller = context.getBean(StagingAuthProbeController.class);

                    assertThat(controller.probe().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
                    assertThat(controller.probe().getBody()).isNull();
                });
    }
}
