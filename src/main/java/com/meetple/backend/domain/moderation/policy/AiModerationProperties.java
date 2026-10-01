package com.meetple.backend.domain.moderation.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ai.moderation")
public record AiModerationProperties(boolean enabled, String serviceToken) {

    public AiModerationProperties {
        serviceToken = serviceToken == null ? "" : serviceToken;
        if (enabled && serviceToken.length() < 32) {
            throw new IllegalArgumentException("AI 신고 분석에는 32자 이상의 서비스 키가 필요합니다.");
        }
    }

    @Override
    public String toString() {
        return "AiModerationProperties[enabled=" + enabled + ", serviceToken=[REDACTED]]";
    }
}
