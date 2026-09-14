package com.meetple.backend.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "chat.websocket.inbound-pool-size=2",
        "chat.websocket.outbound-pool-size=3"
})
@ActiveProfiles("test")
class WebSocketExecutorConfigurationTest {

    @Autowired
    @Qualifier("clientInboundChannelExecutor")
    private Executor inboundExecutor;

    @Autowired
    @Qualifier("clientOutboundChannelExecutor")
    private Executor outboundExecutor;

    @Test
    void stompChannelWorkerCountsAreConfigurableAndQueuesRemainBounded() {
        assertExecutor(inboundExecutor, 2, 1_000);
        assertExecutor(outboundExecutor, 3, 5_000);
    }

    private void assertExecutor(Executor executor, int poolSize, int queueCapacity) {
        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(poolSize);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(poolSize);
        assertThat(taskExecutor.getThreadPoolExecutor().getQueue().remainingCapacity())
                .isEqualTo(queueCapacity);
    }
}
