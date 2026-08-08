package com.meetple.backend.domain.push.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.push.fcm.PushSendResult;
import com.meetple.backend.domain.push.service.PushDeviceTarget;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushDeliveryServiceTest {

    @Mock
    private PushEventDeliveryRepository pushEventDeliveryRepository;

    @InjectMocks
    private PushDeliveryService pushDeliveryService;

    @Test
    void skipsAlreadySentTargetOnDuplicateConsumption() {
        UUID eventId = UUID.randomUUID();
        PushEventDelivery sent = PushEventDelivery.create(eventId, 10L);
        sent.startAttempt();
        sent.markSent();
        given(pushEventDeliveryRepository.findAllByEventIdAndDeviceTokenIdIn(eventId, List.of(10L)))
                .willReturn(List.of(sent));

        List<PushDeviceTarget> pending = pushDeliveryService.prepare(
                eventId,
                List.of(new PushDeviceTarget(10L, "token"))
        );

        assertThat(pending).isEmpty();
    }

    @Test
    void recordsSuccessfulAndInvalidTokenResults() {
        UUID eventId = UUID.randomUUID();
        PushEventDelivery sent = PushEventDelivery.create(eventId, 10L);
        PushEventDelivery invalid = PushEventDelivery.create(eventId, 11L);
        given(pushEventDeliveryRepository.findAllByEventIdAndDeviceTokenIdIn(eventId, List.of(10L)))
                .willReturn(List.of(sent));
        given(pushEventDeliveryRepository.findAllByEventIdAndDeviceTokenIdIn(eventId, List.of(11L)))
                .willReturn(List.of(invalid));

        pushDeliveryService.record(
                eventId,
                new PushSendResult(List.of(10L), List.of(11L), List.of())
        );

        assertThat(sent.getStatus()).isEqualTo(PushDeliveryStatus.SENT);
        assertThat(invalid.getStatus()).isEqualTo(PushDeliveryStatus.INVALID_TOKEN);
        verify(pushEventDeliveryRepository)
                .findAllByEventIdAndDeviceTokenIdIn(eventId, List.of(10L));
    }

    @Test
    void recordsPerTargetFailure() {
        UUID eventId = UUID.randomUUID();
        PushEventDelivery failed = PushEventDelivery.create(eventId, 10L);
        given(pushEventDeliveryRepository.findByEventIdAndDeviceTokenId(eventId, 10L))
                .willReturn(Optional.of(failed));

        pushDeliveryService.record(
                eventId,
                new PushSendResult(
                        List.of(),
                        List.of(),
                        List.of(new com.meetple.backend.domain.push.fcm.PushSendFailure(10L, "UNAVAILABLE"))
                )
        );

        assertThat(failed.getStatus()).isEqualTo(PushDeliveryStatus.FAILED);
        assertThat(failed.getLastErrorCode()).isEqualTo("UNAVAILABLE");
    }
}
