package com.meetple.backend.domain.push.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.push.fcm.InvalidPushTarget;
import com.meetple.backend.domain.push.fcm.PushSendFailure;
import com.meetple.backend.domain.push.fcm.PushSendResult;
import com.meetple.backend.domain.push.service.PushDeviceTarget;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class PushDeliveryServiceTest {

    @Mock
    private PushEventDeliveryRepository pushEventDeliveryRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private PushDeliveryService pushDeliveryService;

    @BeforeEach
    void setUpTransactionTemplate() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    @Test
    void skipsAlreadySentTargetOnDuplicateConsumption() {
        UUID eventId = UUID.randomUUID();
        PushEventDelivery sent = claimedDelivery(eventId, 10L, UUID.randomUUID());
        sent.markSent();
        given(pushEventDeliveryRepository.findAllForUpdate(eventId, List.of(10L)))
                .willReturn(List.of(sent));

        PushDeliveryClaim claim = pushDeliveryService.prepare(
                eventId,
                List.of(new PushDeviceTarget(10L, "token"))
        );

        assertThat(claim.targets()).isEmpty();
        assertThat(claim.blockedByActiveClaim()).isFalse();
    }

    @Test
    void blocksDuplicateConsumerWhileClaimLeaseIsActive() {
        UUID eventId = UUID.randomUUID();
        PushEventDelivery active = claimedDelivery(eventId, 10L, UUID.randomUUID());
        given(pushEventDeliveryRepository.findAllForUpdate(eventId, List.of(10L)))
                .willReturn(List.of(active));

        PushDeliveryClaim claim = pushDeliveryService.prepare(
                eventId,
                List.of(new PushDeviceTarget(10L, "token"))
        );

        assertThat(claim.blockedByActiveClaim()).isTrue();
        assertThat(claim.targets()).isEmpty();
        verify(pushEventDeliveryRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void reclaimsDeliveryAfterLeaseExpires() {
        UUID eventId = UUID.randomUUID();
        UUID expiredOwner = UUID.randomUUID();
        PushEventDelivery expired = PushEventDelivery.create(eventId, 10L);
        LocalDateTime past = LocalDateTime.now().minusMinutes(10);
        expired.claim(expiredOwner, past, past.plusMinutes(5));
        given(pushEventDeliveryRepository.findAllForUpdate(eventId, List.of(10L)))
                .willReturn(List.of(expired));
        PushDeviceTarget target = new PushDeviceTarget(10L, "token");

        PushDeliveryClaim claim = pushDeliveryService.prepare(eventId, List.of(target));

        assertThat(claim.blockedByActiveClaim()).isFalse();
        assertThat(claim.targets()).containsExactly(target);
        assertThat(claim.claimId()).isNotEqualTo(expiredOwner);
        assertThat(expired.getClaimId()).isEqualTo(claim.claimId());
        assertThat(expired.getAttempts()).isEqualTo(2);
    }

    @Test
    void retriesWhenConcurrentInsertWinsUniqueConstraint() {
        UUID eventId = UUID.randomUUID();
        given(pushEventDeliveryRepository.findAllForUpdate(eventId, List.of(10L)))
                .willReturn(List.of());
        given(pushEventDeliveryRepository.saveAllAndFlush(any()))
                .willThrow(new DataIntegrityViolationException("concurrent claim"))
                .willReturn(List.of());

        PushDeliveryClaim claim = pushDeliveryService.prepare(
                eventId,
                List.of(new PushDeviceTarget(10L, "token"))
        );

        assertThat(claim.targets()).hasSize(1);
        verify(transactionTemplate, times(2)).execute(any());
    }

    @Test
    void recordsResultsOnlyForCurrentClaimOwner() {
        UUID eventId = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        PushEventDelivery sent = claimedDelivery(eventId, 10L, claimId);
        PushEventDelivery invalid = claimedDelivery(eventId, 11L, claimId);
        given(pushEventDeliveryRepository.findAllForUpdate(
                eq(eventId),
                argThat(ids -> ids.size() == 2 && ids.containsAll(List.of(10L, 11L)))
        )).willReturn(List.of(sent, invalid));

        pushDeliveryService.record(
                eventId,
                claimId,
                new PushSendResult(
                        List.of(10L),
                        List.of(new InvalidPushTarget(11L, "hash-11")),
                        List.of()
                )
        );

        assertThat(sent.getStatus()).isEqualTo(PushDeliveryStatus.SENT);
        assertThat(invalid.getStatus()).isEqualTo(PushDeliveryStatus.INVALID_TOKEN);
        assertThat(sent.getClaimId()).isNull();
        assertThat(invalid.getClaimId()).isNull();
    }

    @Test
    void staleClaimOwnerCannotOverwriteNewerAttempt() {
        UUID eventId = UUID.randomUUID();
        UUID currentClaimId = UUID.randomUUID();
        PushEventDelivery delivery = claimedDelivery(eventId, 10L, currentClaimId);
        given(pushEventDeliveryRepository.findAllForUpdate(
                eq(eventId),
                argThat(ids -> ids.size() == 1 && ids.contains(10L))
        )).willReturn(List.of(delivery));

        pushDeliveryService.record(
                eventId,
                UUID.randomUUID(),
                new PushSendResult(List.of(10L), List.of(), List.of())
        );

        assertThat(delivery.getStatus()).isEqualTo(PushDeliveryStatus.PENDING);
        assertThat(delivery.getClaimId()).isEqualTo(currentClaimId);
    }

    @Test
    void recordsMultipleFailuresWithOneLockedBulkLookup() {
        UUID eventId = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        PushEventDelivery first = claimedDelivery(eventId, 10L, claimId);
        PushEventDelivery second = claimedDelivery(eventId, 11L, claimId);
        PushEventDelivery third = claimedDelivery(eventId, 12L, claimId);
        given(pushEventDeliveryRepository.findAllForUpdate(
                eq(eventId),
                argThat(ids -> ids.size() == 3 && ids.containsAll(List.of(10L, 11L, 12L)))
        )).willReturn(List.of(first, second, third));

        pushDeliveryService.record(
                eventId,
                claimId,
                new PushSendResult(
                        List.of(),
                        List.of(),
                        List.of(
                                new PushSendFailure(10L, "UNAVAILABLE"),
                                new PushSendFailure(11L, "UNAVAILABLE"),
                                new PushSendFailure(12L, "UNAVAILABLE")
                        )
                )
        );

        assertThat(List.of(first, second, third))
                .allMatch(delivery -> delivery.getStatus() == PushDeliveryStatus.FAILED);
        verify(pushEventDeliveryRepository, times(1)).findAllForUpdate(
                eq(eventId),
                argThat(ids -> ids.size() == 3 && ids.containsAll(List.of(10L, 11L, 12L)))
        );
    }

    private PushEventDelivery claimedDelivery(UUID eventId, Long targetId, UUID claimId) {
        PushEventDelivery delivery = PushEventDelivery.create(eventId, targetId);
        LocalDateTime now = LocalDateTime.now();
        delivery.claim(claimId, now, now.plusMinutes(5));
        return delivery;
    }
}
