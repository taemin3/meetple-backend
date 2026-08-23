package com.meetple.backend.domain.auth.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetple.backend.domain.auth.config.EmailVerificationProperties;
import com.meetple.backend.domain.auth.mail.EmailVerificationMailSender;
import com.meetple.backend.domain.outbox.event.OutboxEventEnvelope;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "auth.email-delivery.kafka",
        name = "consumer-enabled",
        havingValue = "true"
)
public class EmailDeliveryEventProcessor {

    private final ObjectMapper objectMapper;
    private final EmailDeliveryRepository emailDeliveryRepository;
    private final EmailDeliveryService emailDeliveryService;
    private final EmailVerificationMailSender mailSender;
    private final EmailVerificationProperties properties;

    public void process(String payload) {
        UUID deliveryId = parseDeliveryId(payload);
        Optional<PendingEmailDelivery> optionalDelivery = emailDeliveryRepository.find(deliveryId);
        if (optionalDelivery.isEmpty()) {
            return;
        }
        if (!emailDeliveryRepository.tryClaim(deliveryId)) {
            throw new EmailDeliveryProcessingException(
                    "Email delivery is already being processed: " + deliveryId
            );
        }

        PendingEmailDelivery delivery = optionalDelivery.get();
        try {
            if (delivery.deliver()) {
                send(delivery);
            }
            emailDeliveryRepository.delete(deliveryId);
        } catch (RuntimeException exception) {
            emailDeliveryRepository.releaseClaim(deliveryId);
            throw new EmailDeliveryProcessingException(
                    "Email delivery failed: " + deliveryId,
                    exception
            );
        }
    }

    public void discard(String payload) {
        emailDeliveryService.discard(parseDeliveryId(payload));
    }

    private void send(PendingEmailDelivery delivery) {
        switch (delivery.purpose()) {
            case SIGNUP_VERIFICATION -> mailSender.sendVerificationCode(
                    delivery.recipient(),
                    delivery.code(),
                    properties.codeTtl()
            );
            case PASSWORD_RESET -> mailSender.sendPasswordResetCode(
                    delivery.recipient(),
                    delivery.code(),
                    properties.codeTtl()
            );
        }
    }

    private UUID parseDeliveryId(String payload) {
        OutboxEventEnvelope envelope = parseEnvelope(payload);
        validateEnvelope(envelope);
        JsonNode deliveryId = envelope.data().get("deliveryId");
        if (deliveryId == null || !deliveryId.isTextual()) {
            throw new NonRetryableEmailDeliveryException(
                    "deliveryId must be a UUID string."
            );
        }
        try {
            UUID parsed = UUID.fromString(deliveryId.textValue());
            if (!parsed.toString().equals(envelope.aggregateId())) {
                throw new NonRetryableEmailDeliveryException(
                        "Email delivery event identity does not match deliveryId."
                );
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new NonRetryableEmailDeliveryException(
                    "deliveryId must be a UUID string.",
                    exception
            );
        }
    }

    private OutboxEventEnvelope parseEnvelope(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw new NonRetryableEmailDeliveryException(
                    "Email delivery event payload must not be blank."
            );
        }
        try {
            OutboxEventEnvelope envelope = objectMapper.readValue(
                    payload,
                    OutboxEventEnvelope.class
            );
            if (envelope == null) {
                throw new NonRetryableEmailDeliveryException(
                        "Email delivery event payload must not be JSON null."
                );
            }
            return envelope;
        } catch (JsonProcessingException exception) {
            throw new NonRetryableEmailDeliveryException(
                    "Invalid email delivery event JSON.",
                    exception
            );
        }
    }

    private void validateEnvelope(OutboxEventEnvelope envelope) {
        if (envelope.eventId() == null
                || !EmailDeliveryService.EVENT_TYPE.equals(envelope.eventType())
                || envelope.schemaVersion() != EmailDeliveryService.SCHEMA_VERSION
                || !"emailDelivery".equals(envelope.aggregateType())
                || !StringUtils.hasText(envelope.aggregateId())
                || envelope.data() == null
                || !envelope.data().isObject()) {
            throw new NonRetryableEmailDeliveryException(
                    "Unsupported email delivery event contract."
            );
        }
    }
}
