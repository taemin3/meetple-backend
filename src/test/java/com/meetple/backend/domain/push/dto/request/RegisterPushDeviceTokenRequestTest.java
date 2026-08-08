package com.meetple.backend.domain.push.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.meetple.backend.domain.push.entity.PushDevicePlatform;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class RegisterPushDeviceTokenRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsValidAndroidToken() {
        RegisterPushDeviceTokenRequest request = new RegisterPushDeviceTokenRequest(
                "installation-id",
                "fcm-token",
                PushDevicePlatform.ANDROID
        );

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsBlankDeviceIdAndToken() {
        RegisterPushDeviceTokenRequest request = new RegisterPushDeviceTokenRequest(
                " ",
                " ",
                PushDevicePlatform.ANDROID
        );

        assertThat(validator.validate(request)).hasSize(2);
    }
}
