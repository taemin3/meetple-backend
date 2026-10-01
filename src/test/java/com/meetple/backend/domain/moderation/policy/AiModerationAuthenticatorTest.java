package com.meetple.backend.domain.moderation.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meetple.backend.global.exception.BaseException;
import com.meetple.backend.global.response.ErrorStatus;
import org.junit.jupiter.api.Test;

class AiModerationAuthenticatorTest {
    private static final String TOKEN = "moderation-service-token-1234567890";

    @Test
    void acceptsOnlyExactServiceTokenWhenEnabled() {
        var authenticator = new AiModerationAuthenticator(new AiModerationProperties(true, TOKEN));

        assertThatCode(() -> authenticator.verify(TOKEN)).doesNotThrowAnyException();
        assertThatThrownBy(() -> authenticator.verify("wrong-token"))
                .isInstanceOf(BaseException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                        ((BaseException) error).getErrorStatus()
                ).isEqualTo(ErrorStatus.FORBIDDEN));
    }

    @Test
    void rejectsAllRequestsWhenFeatureIsDisabled() {
        var authenticator = new AiModerationAuthenticator(new AiModerationProperties(false, ""));

        assertThatThrownBy(() -> authenticator.verify(TOKEN))
                .isInstanceOf(BaseException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                        ((BaseException) error).getErrorStatus()
                ).isEqualTo(ErrorStatus.AI_MODERATION_UNAVAILABLE));
    }

    @Test
    void enabledConfigurationRequiresLongServiceToken() {
        assertThatThrownBy(() -> new AiModerationProperties(true, "short"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
