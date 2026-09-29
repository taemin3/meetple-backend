package com.meetple.backend.domain.ai;

import com.meetple.backend.global.exception.BaseException;
import com.meetple.backend.global.response.ErrorStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Spring만 발급하는 90초 유효 읽기 전용 권한. 로그인 JWT를 AI 서비스에 전달하지 않는다. */
@Component
public class AiSearchCapability {
    private final AiSearchProperties properties;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public AiSearchCapability(AiSearchProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AiSearchCapability(AiSearchProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String issue(long memberId) {
        requireEnabled();
        String payload = "meeting-search-v1." + memberId + "."
                + (clock.instant().getEpochSecond() + 90) + "." + UUID.randomUUID();
        return payload + "." + sign(payload);
    }

    public long verify(String serviceToken, String capability) {
        requireEnabled();
        try {
            if (!equal(properties.serviceToken(), serviceToken) || capability == null || capability.length() > 300) {
                throw denied();
            }
            String[] parts = capability.split("\\.");
            if (parts.length != 5 || !parts[0].equals("meeting-search-v1")) throw denied();
            String payload = String.join(".", parts[0], parts[1], parts[2], parts[3]);
            if (!equal(sign(payload), parts[4])) throw denied();
            long expiry = Long.parseLong(parts[2]);
            long now = clock.instant().getEpochSecond();
            long memberId = Long.parseLong(parts[1]);
            if (memberId <= 0 || expiry <= now || expiry > now + 90) throw denied();
            return memberId;
        } catch (IllegalArgumentException ex) {
            throw denied();
        }
    }

    public void requireEnabled() {
        if (!properties.enabled()) throw new BaseException(ErrorStatus.AI_SEARCH_UNAVAILABLE);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.capabilitySecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("AI 권한 서명을 생성할 수 없습니다.");
        }
    }

    private static boolean equal(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private static BaseException denied() { return new BaseException(ErrorStatus.FORBIDDEN); }
}
