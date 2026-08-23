package com.meetple.backend.domain.auth.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.auth.repository.EmailVerificationRepository;
import com.meetple.backend.domain.auth.repository.PasswordResetRepository;
import com.meetple.backend.domain.outbox.event.OutboxEventTopic;
import com.meetple.backend.domain.outbox.service.OutboxEventPublisher;
import com.meetple.backend.domain.outbox.service.OutboxEventRequest;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryServiceTest {

    @Mock
    private EmailDeliveryRepository emailDeliveryRepository;
    @Mock
    private EmailVerificationRepository emailVerificationRepository;
    @Mock
    private PasswordResetRepository passwordResetRepository;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    private EmailDeliveryService service;

    @BeforeEach
    void setUp() {
        service = new EmailDeliveryService(
                emailDeliveryRepository,
                emailVerificationRepository,
                passwordResetRepository,
                outboxEventPublisher
        );
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void scheduleStoresSecretInRedisAndPublishesOnlyDeliveryId() {
        ArgumentCaptor<PendingEmailDelivery> deliveryCaptor =
                ArgumentCaptor.forClass(PendingEmailDelivery.class);
        ArgumentCaptor<OutboxEventRequest> eventCaptor =
                ArgumentCaptor.forClass(OutboxEventRequest.class);

        UUID deliveryId = service.schedule(
                EmailDeliveryPurpose.SIGNUP_VERIFICATION,
                "user@meetple.com",
                "123456",
                "code-hash",
                true,
                Duration.ofMinutes(5)
        );

        verify(emailDeliveryRepository).save(deliveryCaptor.capture(), any());
        verify(outboxEventPublisher).publish(eventCaptor.capture());
        assertThat(deliveryCaptor.getValue().deliveryId()).isEqualTo(deliveryId);
        assertThat(deliveryCaptor.getValue().code()).isEqualTo("123456");
        OutboxEventRequest event = eventCaptor.getValue();
        assertThat(event.topic()).isEqualTo(OutboxEventTopic.EMAIL_DELIVERY);
        assertThat(event.aggregateId()).isEqualTo(deliveryId.toString());
        assertThat(event.data()).isEqualTo(Map.of("deliveryId", deliveryId.toString()));
        assertThat(event.data().toString())
                .doesNotContain("user@meetple.com", "123456", "code-hash");
    }

    @Test
    void scheduleFailureDeletesPayloadAndMatchingChallenge() {
        doThrow(new IllegalStateException("outbox unavailable"))
                .when(outboxEventPublisher).publish(any());
        given(emailDeliveryRepository.find(any())).willAnswer(invocation -> Optional.of(
                delivery(invocation.getArgument(0), EmailDeliveryPurpose.PASSWORD_RESET)
        ));

        assertThatThrownBy(() -> service.schedule(
                EmailDeliveryPurpose.PASSWORD_RESET,
                "user@meetple.com",
                "123456",
                "code-hash",
                true,
                Duration.ofMinutes(5)
        )).isInstanceOf(IllegalStateException.class);

        verify(passwordResetRepository)
                .deleteChallengeIfMatches("user@meetple.com", "code-hash");
        verify(emailVerificationRepository, never())
                .deleteChallengeIfMatches(any(), any());
        verify(emailDeliveryRepository).delete(any());
    }

    @Test
    void rollbackDeletesPayloadAndSignupChallenge() {
        TransactionSynchronizationManager.initSynchronization();
        given(emailDeliveryRepository.find(any())).willAnswer(invocation -> Optional.of(
                delivery(invocation.getArgument(0), EmailDeliveryPurpose.SIGNUP_VERIFICATION)
        ));

        UUID deliveryId = service.schedule(
                EmailDeliveryPurpose.SIGNUP_VERIFICATION,
                "user@meetple.com",
                "123456",
                "code-hash",
                true,
                Duration.ofMinutes(5)
        );
        TransactionSynchronization synchronization = TransactionSynchronizationManager
                .getSynchronizations().getFirst();

        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(emailVerificationRepository)
                .deleteChallengeIfMatches("user@meetple.com", "code-hash");
        verify(emailDeliveryRepository).delete(deliveryId);
    }

    @Test
    void remainingChallengeTtlUsesPurposeSpecificRepository() {
        PendingEmailDelivery signup = delivery(
                UUID.randomUUID(),
                EmailDeliveryPurpose.SIGNUP_VERIFICATION
        );
        PendingEmailDelivery passwordReset = delivery(
                UUID.randomUUID(),
                EmailDeliveryPurpose.PASSWORD_RESET
        );
        given(emailVerificationRepository.findChallengeRemainingTtlIfMatches(
                "user@meetple.com",
                "code-hash"
        )).willReturn(Duration.ofMinutes(4));
        given(passwordResetRepository.findChallengeRemainingTtlIfMatches(
                "user@meetple.com",
                "code-hash"
        )).willReturn(Duration.ofMinutes(3));

        assertThat(service.findRemainingChallengeTtl(signup))
                .isEqualTo(Duration.ofMinutes(4));
        assertThat(service.findRemainingChallengeTtl(passwordReset))
                .isEqualTo(Duration.ofMinutes(3));
    }

    private PendingEmailDelivery delivery(UUID id, EmailDeliveryPurpose purpose) {
        return new PendingEmailDelivery(
                id,
                purpose,
                "user@meetple.com",
                "123456",
                "code-hash",
                true
        );
    }
}
