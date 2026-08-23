package com.meetple.backend.domain.auth.email;

import com.meetple.backend.domain.auth.repository.EmailVerificationRepository;
import com.meetple.backend.domain.auth.repository.PasswordResetRepository;
import com.meetple.backend.domain.outbox.event.OutboxEventTopic;
import com.meetple.backend.domain.outbox.service.OutboxEventPublisher;
import com.meetple.backend.domain.outbox.service.OutboxEventRequest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailDeliveryService {

    static final String EVENT_TYPE = "EMAIL_DELIVERY_REQUESTED";
    static final int SCHEMA_VERSION = 1;

    private final EmailDeliveryRepository emailDeliveryRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID schedule(
            EmailDeliveryPurpose purpose,
            String recipient,
            String code,
            String codeHash,
            boolean deliver,
            Duration ttl
    ) {
        UUID deliveryId = UUID.randomUUID();
        PendingEmailDelivery delivery = new PendingEmailDelivery(
                deliveryId,
                purpose,
                recipient,
                code,
                codeHash,
                deliver
        );

        try {
            emailDeliveryRepository.save(delivery, ttl);
            registerRollbackCleanup(deliveryId);
            outboxEventPublisher.publish(toOutboxRequest(deliveryId));
            return deliveryId;
        } catch (RuntimeException exception) {
            discardQuietly(deliveryId, "scheduling failure");
            throw exception;
        }
    }

    public void discard(UUID deliveryId) {
        try {
            emailDeliveryRepository.find(deliveryId).ifPresent(delivery -> {
                switch (delivery.purpose()) {
                    case SIGNUP_VERIFICATION -> emailVerificationRepository
                            .deleteChallengeIfMatches(delivery.recipient(), delivery.codeHash());
                    case PASSWORD_RESET -> passwordResetRepository
                            .deleteChallengeIfMatches(delivery.recipient(), delivery.codeHash());
                }
            });
        } finally {
            emailDeliveryRepository.delete(deliveryId);
        }
    }

    public Duration findRemainingChallengeTtl(PendingEmailDelivery delivery) {
        return switch (delivery.purpose()) {
            case SIGNUP_VERIFICATION -> emailVerificationRepository
                    .findChallengeRemainingTtlIfMatches(
                            delivery.recipient(),
                            delivery.codeHash()
                    );
            case PASSWORD_RESET -> passwordResetRepository
                    .findChallengeRemainingTtlIfMatches(
                            delivery.recipient(),
                            delivery.codeHash()
                    );
        };
    }

    private OutboxEventRequest toOutboxRequest(UUID deliveryId) {
        String id = deliveryId.toString();
        return new OutboxEventRequest(
                "emailDelivery",
                id,
                EVENT_TYPE,
                id,
                OutboxEventTopic.EMAIL_DELIVERY,
                SCHEMA_VERSION,
                "email-delivery:" + id,
                Map.of("deliveryId", id)
        );
    }

    private void registerRollbackCleanup(UUID deliveryId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    discardQuietly(deliveryId, "transaction rollback");
                }
            }
        });
    }

    private void discardQuietly(UUID deliveryId, String reason) {
        try {
            discard(deliveryId);
        } catch (RuntimeException cleanupException) {
            log.warn(
                    "Failed to clean up email delivery after {}: {}",
                    reason,
                    deliveryId,
                    cleanupException
            );
        }
    }
}
