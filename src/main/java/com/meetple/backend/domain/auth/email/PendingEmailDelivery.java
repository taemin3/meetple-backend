package com.meetple.backend.domain.auth.email;

import java.util.UUID;

public record PendingEmailDelivery(
        UUID deliveryId,
        EmailDeliveryPurpose purpose,
        String recipient,
        String code,
        String codeHash,
        boolean deliver
) {
}
