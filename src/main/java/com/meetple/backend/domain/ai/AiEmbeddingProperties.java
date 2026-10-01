package com.meetple.backend.domain.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ai.embedding")
public record AiEmbeddingProperties(boolean enabled) {
}
