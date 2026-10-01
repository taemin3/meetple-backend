package com.meetple.backend.domain.moderation.policy;

import com.meetple.backend.global.exception.BaseException;
import com.meetple.backend.global.response.ErrorStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiModerationAuthenticator {
    private final AiModerationProperties properties;

    public void verify(String suppliedToken) {
        if (!properties.enabled()) {
            throw new BaseException(ErrorStatus.AI_MODERATION_UNAVAILABLE);
        }
        if (suppliedToken == null || !MessageDigest.isEqual(
                properties.serviceToken().getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new BaseException(ErrorStatus.FORBIDDEN);
        }
    }
}
