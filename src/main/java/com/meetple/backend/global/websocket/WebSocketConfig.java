package com.meetple.backend.global.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final String STOMP_ENDPOINT = "/ws";
    public static final String APPLICATION_PREFIX = "/app";
    public static final String TOPIC_PREFIX = "/topic";
    public static final String USER_PREFIX = "/user";

    private static final long HEARTBEAT_INTERVAL_MILLIS = 10_000L;
    private static final int CLIENT_INBOUND_QUEUE_CAPACITY = 1_000;
    private static final int CLIENT_OUTBOUND_QUEUE_CAPACITY = 5_000;

    private final ChatStompChannelInterceptor chatStompChannelInterceptor;
    private final ChatStompErrorHandler chatStompErrorHandler;
    private final LocalChatWebSocketSessionRegistry sessionRegistry;
    private final int clientInboundPoolSize;
    private final int clientOutboundPoolSize;

    public WebSocketConfig(
            ChatStompChannelInterceptor chatStompChannelInterceptor,
            ChatStompErrorHandler chatStompErrorHandler,
            LocalChatWebSocketSessionRegistry sessionRegistry,
            @Value("${chat.websocket.inbound-pool-size}") int clientInboundPoolSize,
            @Value("${chat.websocket.outbound-pool-size}") int clientOutboundPoolSize
    ) {
        this.chatStompChannelInterceptor = chatStompChannelInterceptor;
        this.chatStompErrorHandler = chatStompErrorHandler;
        this.sessionRegistry = sessionRegistry;
        this.clientInboundPoolSize = requireValidPoolSize(
                "chat.websocket.inbound-pool-size",
                clientInboundPoolSize
        );
        this.clientOutboundPoolSize = requireValidPoolSize(
                "chat.websocket.outbound-pool-size",
                clientOutboundPoolSize
        );
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.setErrorHandler(chatStompErrorHandler);
        registry.addEndpoint(STOMP_ENDPOINT);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes(APPLICATION_PREFIX);
        registry.setUserDestinationPrefix(USER_PREFIX);
        registry.enableSimpleBroker(TOPIC_PREFIX, "/queue")
                .setTaskScheduler(chatMessageBrokerTaskScheduler())
                .setHeartbeatValue(new long[]{
                        HEARTBEAT_INTERVAL_MILLIS,
                        HEARTBEAT_INTERVAL_MILLIS
                });
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.executor(channelExecutor(
                "clientInboundChannel-",
                clientInboundPoolSize,
                CLIENT_INBOUND_QUEUE_CAPACITY
        ));
        registration.interceptors(chatStompChannelInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.executor(channelExecutor(
                "clientOutboundChannel-",
                clientOutboundPoolSize,
                CLIENT_OUTBOUND_QUEUE_CAPACITY
        ));
        registration.interceptors(chatStompChannelInterceptor);
    }

    private ThreadPoolTaskExecutor channelExecutor(
            String threadNamePrefix,
            int poolSize,
            int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        return executor;
    }

    private int requireValidPoolSize(String propertyName, int poolSize) {
        if (poolSize < 1 || poolSize > 64) {
            throw new IllegalArgumentException(propertyName + " must be between 1 and 64");
        }
        return poolSize;
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(
                    org.springframework.web.socket.WebSocketSession session
            ) throws Exception {
                sessionRegistry.registerTransport(session);
                try {
                    super.afterConnectionEstablished(session);
                } catch (Exception exception) {
                    sessionRegistry.remove(session.getId());
                    throw exception;
                }
            }

            @Override
            public void afterConnectionClosed(
                    org.springframework.web.socket.WebSocketSession session,
                    org.springframework.web.socket.CloseStatus closeStatus
            ) throws Exception {
                try {
                    super.afterConnectionClosed(session, closeStatus);
                } finally {
                    sessionRegistry.remove(session.getId());
                }
            }
        });
    }

    @Bean
    public ThreadPoolTaskScheduler chatMessageBrokerTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("chat-stomp-heartbeat-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
