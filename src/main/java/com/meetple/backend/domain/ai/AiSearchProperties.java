package com.meetple.backend.domain.ai;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ai.search")
public record AiSearchProperties(
        boolean enabled, URI baseUrl, String serviceToken, String capabilitySecret, Duration timeout
) {
    public AiSearchProperties {
        baseUrl = baseUrl == null ? URI.create("http://127.0.0.1:8001") : baseUrl;
        timeout = timeout == null ? Duration.ofSeconds(45) : timeout;
        if (enabled && (serviceToken == null || serviceToken.length() < 32
                || capabilitySecret == null || capabilitySecret.length() < 32)) {
            throw new IllegalArgumentException("AI 검색에는 서로 다른 32자 이상의 서비스 키와 권한 서명 키가 필요합니다.");
        }
        if (enabled && serviceToken.equals(capabilitySecret)) {
            throw new IllegalArgumentException("AI 서비스 키와 권한 서명 키는 달라야 합니다.");
        }
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(60)) > 0) {
            throw new IllegalArgumentException("AI 검색 제한 시간은 0초 초과 60초 이하이어야 합니다.");
        }
    }

    @Override
    public String toString() {
        return "AiSearchProperties[enabled=" + enabled + ", baseUrl=" + baseUrl
                + ", serviceToken=[REDACTED], capabilitySecret=[REDACTED], timeout=" + timeout + "]";
    }
}
