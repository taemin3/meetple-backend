package com.meetple.backend.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WebSocketExecutorConfigurationTest {

    @Autowired
    @Qualifier("clientInboundChannelExecutor")
    private Executor inboundExecutor;

    @Autowired
    @Qualifier("clientOutboundChannelExecutor")
    private Executor outboundExecutor;

    @Test
    void stompChannelsUseFourBoundedWorkers() {
        assertExecutor(inboundExecutor, 1_000);
        assertExecutor(outboundExecutor, 5_000);
    }

    private void assertExecutor(Executor executor, int queueCapacity) {
        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(4);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(4);
        assertThat(taskExecutor.getThreadPoolExecutor().getQueue().remainingCapacity())
                .isEqualTo(queueCapacity);
    }
}
