package com.meetple.backend.domain.ai;

import static org.assertj.core.api.Assertions.*;

import com.meetple.backend.global.exception.BaseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AiSearchCapabilityTest {
    static final String SERVICE_KEY = "test-service-key-000000000000000000";
    static final AiSearchProperties PROPERTIES = new AiSearchProperties(true, null, SERVICE_KEY,
            "test-capability-secret-0000000000000", null);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-29T00:00:00Z"), ZoneOffset.UTC);

    @Test void onlyValidServiceAndSignedMemberAreAccepted() {
        var verifier = new AiSearchCapability(PROPERTIES, clock);
        String token = verifier.issue(42);
        assertThat(verifier.verify(SERVICE_KEY, token)).isEqualTo(42);
        assertThatThrownBy(() -> verifier.verify("wrong", token)).isInstanceOf(BaseException.class);
        assertThatThrownBy(() -> verifier.verify(SERVICE_KEY, token.replace(".42.", ".43.")))
                .isInstanceOf(BaseException.class);
        assertThatThrownBy(() -> verifier.verify(SERVICE_KEY, null)).isInstanceOf(BaseException.class);
    }

    @Test void expiresAtNinetySecondsAndCannotBeUsedForAnotherAudience() {
        String token = new AiSearchCapability(PROPERTIES, clock).issue(42);
        var expired = new AiSearchCapability(PROPERTIES, Clock.offset(clock, Duration.ofSeconds(90)));
        assertThatThrownBy(() -> expired.verify(SERVICE_KEY, token)).isInstanceOf(BaseException.class);
        var verifier = new AiSearchCapability(PROPERTIES, clock);
        assertThatThrownBy(() -> verifier.verify(SERVICE_KEY, token.replace("meeting-search-v1", "chat")))
                .isInstanceOf(BaseException.class);
    }

    @Test void disabledFeatureCannotIssueOrAcceptCapabilities() {
        var verifier = new AiSearchCapability(new AiSearchProperties(false, null, null, null, null), clock);
        assertThatThrownBy(() -> verifier.issue(42)).isInstanceOf(BaseException.class);
        assertThatThrownBy(() -> verifier.verify(SERVICE_KEY, "anything")).isInstanceOf(BaseException.class);
    }
}
