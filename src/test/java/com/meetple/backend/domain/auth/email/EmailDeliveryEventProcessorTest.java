package com.meetple.backend.domain.auth.email;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetple.backend.domain.auth.config.EmailVerificationProperties;
import com.meetple.backend.domain.auth.mail.EmailVerificationMailSender;
import com.meetple.backend.domain.outbox.event.OutboxEventEnvelope;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryEventProcessorTest {

    private static final UUID DELIVERY_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
    );
    private static final Duration CODE_TTL = Duration.ofMinutes(5);

    @Mock
    private EmailDeliveryRepository emailDeliveryRepository;
    @Mock
    private EmailDeliveryService emailDeliveryService;
    @Mock
    private EmailVerificationMailSender mailSender;

    private ObjectMapper objectMapper;
    private EmailDeliveryEventProcessor processor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        processor = new EmailDeliveryEventProcessor(
                objectMapper,
                emailDeliveryRepository,
                emailDeliveryService,
                mailSender,
                properties()
        );
    }

    @Test
    void sendsSignupVerificationAndDeletesRedisPayload() throws Exception {
        PendingEmailDelivery delivery = delivery(
                EmailDeliveryPurpose.SIGNUP_VERIFICATION,
                true
        );
        given(emailDeliveryRepository.find(DELIVERY_ID)).willReturn(Optional.of(delivery));
        given(emailDeliveryRepository.tryClaim(DELIVERY_ID)).willReturn(true);

        processor.process(payload());

        verify(mailSender).sendVerificationCode("user@meetple.com", "123456", CODE_TTL);
        verify(emailDeliveryRepository).delete(DELIVERY_ID);
    }

    @Test
    void sendsPasswordResetMail() throws Exception {
        PendingEmailDelivery delivery = delivery(EmailDeliveryPurpose.PASSWORD_RESET, true);
        given(emailDeliveryRepository.find(DELIVERY_ID)).willReturn(Optional.of(delivery));
        given(emailDeliveryRepository.tryClaim(DELIVERY_ID)).willReturn(true);

        processor.process(payload());

        verify(mailSender).sendPasswordResetCode("user@meetple.com", "123456", CODE_TTL);
        verify(emailDeliveryRepository).delete(DELIVERY_ID);
    }

    @Test
    void unknownAccountUsesSameEventButSkipsSmtp() throws Exception {
        PendingEmailDelivery delivery = delivery(EmailDeliveryPurpose.PASSWORD_RESET, false);
        given(emailDeliveryRepository.find(DELIVERY_ID)).willReturn(Optional.of(delivery));
        given(emailDeliveryRepository.tryClaim(DELIVERY_ID)).willReturn(true);

        processor.process(payload());

        verify(mailSender, never()).sendPasswordResetCode(anyString(), anyString(), any());
        verify(mailSender, never()).sendVerificationCode(anyString(), anyString(), any());
        verify(emailDeliveryRepository).delete(DELIVERY_ID);
    }

    @Test
    void smtpFailureReleasesClaimForKafkaRetry() throws Exception {
        PendingEmailDelivery delivery = delivery(EmailDeliveryPurpose.PASSWORD_RESET, true);
        given(emailDeliveryRepository.find(DELIVERY_ID)).willReturn(Optional.of(delivery));
        given(emailDeliveryRepository.tryClaim(DELIVERY_ID)).willReturn(true);
        doThrow(new IllegalStateException("SES unavailable"))
                .when(mailSender).sendPasswordResetCode(
                        "user@meetple.com",
                        "123456",
                        CODE_TTL
                );

        assertThatThrownBy(() -> processor.process(payload()))
                .isInstanceOf(EmailDeliveryProcessingException.class)
                .hasCauseInstanceOf(IllegalStateException.class);

        verify(emailDeliveryRepository).releaseClaim(DELIVERY_ID);
        verify(emailDeliveryRepository, never()).delete(DELIVERY_ID);
    }

    @Test
    void expiredRedisPayloadIsAcknowledgedWithoutSending() throws Exception {
        given(emailDeliveryRepository.find(DELIVERY_ID)).willReturn(Optional.empty());

        processor.process(payload());

        verify(emailDeliveryRepository, never()).tryClaim(DELIVERY_ID);
        verify(mailSender, never()).sendPasswordResetCode(anyString(), anyString(), any());
    }

    @Test
    void concurrentClaimIsRetried() throws Exception {
        given(emailDeliveryRepository.find(DELIVERY_ID)).willReturn(Optional.of(
                delivery(EmailDeliveryPurpose.PASSWORD_RESET, true)
        ));
        given(emailDeliveryRepository.tryClaim(DELIVERY_ID)).willReturn(false);

        assertThatThrownBy(() -> processor.process(payload()))
                .isInstanceOf(EmailDeliveryProcessingException.class)
                .hasMessageContaining("already being processed");
    }

    @Test
    void malformedEventIsRejectedWithoutOrdinaryRetry() {
        assertThatThrownBy(() -> processor.process("not-json"))
                .isInstanceOf(NonRetryableEmailDeliveryException.class);
    }

    @Test
    void mismatchedDeliveryIdentityIsRejected() throws Exception {
        UUID otherId = UUID.randomUUID();
        OutboxEventEnvelope envelope = envelope();
        OutboxEventEnvelope mismatched = new OutboxEventEnvelope(
                envelope.eventId(),
                envelope.eventType(),
                envelope.schemaVersion(),
                envelope.occurredAt(),
                envelope.aggregateType(),
                otherId.toString(),
                envelope.data()
        );

        assertThatThrownBy(() -> processor.process(
                objectMapper.writeValueAsString(mismatched)
        )).isInstanceOf(NonRetryableEmailDeliveryException.class)
                .hasMessage("Email delivery event identity does not match deliveryId.");
    }

    @Test
    void discardDelegatesTerminalCleanup() throws Exception {
        processor.discard(payload());

        verify(emailDeliveryService).discard(DELIVERY_ID);
    }

    private String payload() throws Exception {
        return objectMapper.writeValueAsString(envelope());
    }

    private OutboxEventEnvelope envelope() {
        return new OutboxEventEnvelope(
                UUID.randomUUID(),
                EmailDeliveryService.EVENT_TYPE,
                EmailDeliveryService.SCHEMA_VERSION,
                Instant.now().toString(),
                "emailDelivery",
                DELIVERY_ID.toString(),
                objectMapper.valueToTree(Map.of("deliveryId", DELIVERY_ID.toString()))
        );
    }

    private PendingEmailDelivery delivery(EmailDeliveryPurpose purpose, boolean deliver) {
        return new PendingEmailDelivery(
                DELIVERY_ID,
                purpose,
                "user@meetple.com",
                "123456",
                "code-hash",
                deliver
        );
    }

    private EmailVerificationProperties properties() {
        return new EmailVerificationProperties(
                CODE_TTL,
                Duration.ofMinutes(1),
                Duration.ofMinutes(15),
                5,
                Duration.ofMinutes(1),
                5,
                Duration.ofMinutes(1),
                100,
                Duration.ofMinutes(1),
                10,
                "test-email-verification-secret-1234567890",
                "noreply@meetple.test"
        );
    }

}
