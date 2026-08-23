package com.meetple.backend.domain.auth.email;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.mockito.ArgumentCaptor;
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
                mailSender
        );
    }

    @Test
    void sendsSignupVerificationAndDeletesRedisPayload() throws Exception {
        PendingEmailDelivery delivery = delivery(
                EmailDeliveryPurpose.SIGNUP_VERIFICATION,
                true
        );
        given(emailDeliveryRepository.find(DELIVERY_ID)).willReturn(Optional.of(delivery));
        given(emailDeliveryService.findRemainingChallengeTtl(delivery))
                .willReturn(Duration.ofMinutes(3), Duration.ofMinutes(2));
        given(emailDeliveryRepository.tryClaim(
                org.mockito.ArgumentMatchers.eq(DELIVERY_ID),
                anyString(),
                org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(3))
        )).willReturn(true);
        given(emailDeliveryRepository.complete(
                org.mockito.ArgumentMatchers.eq(DELIVERY_ID),
                anyString()
        )).willReturn(true);

        processor.process(payload());

        verify(mailSender).sendVerificationCode(
                "user@meetple.com",
                "123456"
        );
        verifyCompletionUsesClaimOwner(Duration.ofMinutes(3));
    }

    @Test
    void sendsPasswordResetMail() throws Exception {
        PendingEmailDelivery delivery = delivery(EmailDeliveryPurpose.PASSWORD_RESET, true);
        given(emailDeliveryRepository.find(DELIVERY_ID)).willReturn(Optional.of(delivery));
        allowDelivery(delivery);

        processor.process(payload());

        verify(mailSender).sendPasswordResetCode("user@meetple.com", "123456");
    }

    @Test
    void unknownAccountUsesSameEventButSkipsSmtp() throws Exception {
        PendingEmailDelivery delivery = delivery(EmailDeliveryPurpose.PASSWORD_RESET, false);
        given(emailDeliveryRepository.find(DELIVERY_ID)).willReturn(Optional.of(delivery));

        processor.process(payload());

        verify(mailSender, never()).sendPasswordResetCode(anyString(), anyString());
        verify(mailSender, never()).sendVerificationCode(anyString(), anyString());
        verify(emailDeliveryRepository).delete(DELIVERY_ID);
    }

    @Test
    void smtpFailureReleasesClaimForKafkaRetry() throws Exception {
        PendingEmailDelivery delivery = delivery(EmailDeliveryPurpose.PASSWORD_RESET, true);
        given(emailDeliveryRepository.find(DELIVERY_ID)).willReturn(Optional.of(delivery));
        given(emailDeliveryService.findRemainingChallengeTtl(delivery))
                .willReturn(CODE_TTL, CODE_TTL);
        given(emailDeliveryRepository.tryClaim(
                org.mockito.ArgumentMatchers.eq(DELIVERY_ID),
                anyString(),
                org.mockito.ArgumentMatchers.eq(CODE_TTL)
        )).willReturn(true);
        doThrow(new IllegalStateException("SES unavailable"))
                .when(mailSender).sendPasswordResetCode(
                        "user@meetple.com",
                        "123456"
                );

        assertThatThrownBy(() -> processor.process(payload()))
                .isInstanceOf(EmailDeliveryProcessingException.class)
                .hasCauseInstanceOf(IllegalStateException.class);

        ArgumentCaptor<String> ownerCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailDeliveryRepository).tryClaim(
                org.mockito.ArgumentMatchers.eq(DELIVERY_ID),
                ownerCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(CODE_TTL)
        );
        verify(emailDeliveryRepository).releaseClaim(DELIVERY_ID, ownerCaptor.getValue());
        verify(emailDeliveryRepository, never()).delete(DELIVERY_ID);
    }

    @Test
    void expiredRedisPayloadIsAcknowledgedWithoutSending() throws Exception {
        given(emailDeliveryRepository.find(DELIVERY_ID)).willReturn(Optional.empty());

        processor.process(payload());

        verify(emailDeliveryRepository, never()).tryClaim(any(), anyString(), any());
        verify(mailSender, never()).sendPasswordResetCode(anyString(), anyString());
    }

    @Test
    void staleChallengeIsDiscardedBeforeSending() throws Exception {
        PendingEmailDelivery delivery = delivery(EmailDeliveryPurpose.PASSWORD_RESET, true);
        given(emailDeliveryRepository.find(DELIVERY_ID)).willReturn(Optional.of(delivery));
        given(emailDeliveryService.findRemainingChallengeTtl(delivery))
                .willReturn(Duration.ZERO);

        processor.process(payload());

        verify(emailDeliveryRepository).delete(DELIVERY_ID);
        verify(emailDeliveryRepository, never()).tryClaim(any(), anyString(), any());
        verify(mailSender, never()).sendPasswordResetCode(anyString(), anyString());
    }

    @Test
    void challengeReplacedAfterClaimIsDiscardedBeforeSending() throws Exception {
        PendingEmailDelivery delivery = delivery(EmailDeliveryPurpose.PASSWORD_RESET, true);
        given(emailDeliveryRepository.find(DELIVERY_ID)).willReturn(Optional.of(delivery));
        given(emailDeliveryService.findRemainingChallengeTtl(delivery))
                .willReturn(CODE_TTL, Duration.ZERO);
        given(emailDeliveryRepository.tryClaim(
                org.mockito.ArgumentMatchers.eq(DELIVERY_ID),
                anyString(),
                org.mockito.ArgumentMatchers.eq(CODE_TTL)
        )).willReturn(true);

        processor.process(payload());

        verify(emailDeliveryRepository).complete(
                org.mockito.ArgumentMatchers.eq(DELIVERY_ID),
                anyString()
        );
        verify(mailSender, never()).sendPasswordResetCode(anyString(), anyString());
    }

    @Test
    void concurrentClaimIsRetried() throws Exception {
        PendingEmailDelivery delivery = delivery(EmailDeliveryPurpose.PASSWORD_RESET, true);
        given(emailDeliveryRepository.find(DELIVERY_ID)).willReturn(Optional.of(delivery));
        given(emailDeliveryService.findRemainingChallengeTtl(delivery)).willReturn(CODE_TTL);
        given(emailDeliveryRepository.tryClaim(
                org.mockito.ArgumentMatchers.eq(DELIVERY_ID),
                anyString(),
                org.mockito.ArgumentMatchers.eq(CODE_TTL)
        )).willReturn(false);

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

    private void allowDelivery(PendingEmailDelivery delivery) {
        given(emailDeliveryService.findRemainingChallengeTtl(delivery))
                .willReturn(CODE_TTL, CODE_TTL);
        given(emailDeliveryRepository.tryClaim(
                org.mockito.ArgumentMatchers.eq(DELIVERY_ID),
                anyString(),
                org.mockito.ArgumentMatchers.eq(CODE_TTL)
        )).willReturn(true);
        given(emailDeliveryRepository.complete(
                org.mockito.ArgumentMatchers.eq(DELIVERY_ID),
                anyString()
        )).willReturn(true);
    }

    private void verifyCompletionUsesClaimOwner(Duration claimTtl) {
        ArgumentCaptor<String> ownerCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailDeliveryRepository).tryClaim(
                org.mockito.ArgumentMatchers.eq(DELIVERY_ID),
                ownerCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(claimTtl)
        );
        verify(emailDeliveryRepository).complete(DELIVERY_ID, ownerCaptor.getValue());
    }
}
