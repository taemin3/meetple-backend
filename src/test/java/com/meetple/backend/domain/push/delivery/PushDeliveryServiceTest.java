package com.meetple.backend.domain.push.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.push.fcm.PushSendResult;
import com.meetple.backend.domain.push.service.PushDeviceTarget;
import java.util.List;
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
        given(pushEventDeliveryRepository.findAllByEventIdAndDeviceTokenIdIn(
                org.mockito.ArgumentMatchers.eq(eventId),
                argThat(ids -> ids.size() == 2 && ids.containsAll(List.of(10L, 11L)))
        )).willReturn(List.of(sent, invalid));

        pushDeliveryService.record(
                eventId,
                new PushSendResult(List.of(10L), List.of(11L), List.of())
        );

        assertThat(sent.getStatus()).isEqualTo(PushDeliveryStatus.SENT);
        assertThat(invalid.getStatus()).isEqualTo(PushDeliveryStatus.INVALID_TOKEN);
        verify(pushEventDeliveryRepository).findAllByEventIdAndDeviceTokenIdIn(
                org.mockito.ArgumentMatchers.eq(eventId),
                argThat(ids -> ids.size() == 2 && ids.containsAll(List.of(10L, 11L)))
        );
    }

    @Test
    void recordsPerTargetFailure() {
        UUID eventId = UUID.randomUUID();
        PushEventDelivery failed = PushEventDelivery.create(eventId, 10L);
        given(pushEventDeliveryRepository.findAllByEventIdAndDeviceTokenIdIn(
                org.mockito.ArgumentMatchers.eq(eventId),
                argThat(ids -> ids.size() == 1 && ids.contains(10L))
        )).willReturn(List.of(failed));

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

    @Test
    void recordsMultipleFailuresWithOneBulkLookup() {
        UUID eventId = UUID.randomUUID();
        PushEventDelivery first = PushEventDelivery.create(eventId, 10L);
        PushEventDelivery second = PushEventDelivery.create(eventId, 11L);
        PushEventDelivery third = PushEventDelivery.create(eventId, 12L);
        given(pushEventDeliveryRepository.findAllByEventIdAndDeviceTokenIdIn(
                org.mockito.ArgumentMatchers.eq(eventId),
                argThat(ids -> ids.size() == 3 && ids.containsAll(List.of(10L, 11L, 12L)))
        )).willReturn(List.of(first, second, third));

        pushDeliveryService.record(
                eventId,
                new PushSendResult(
                        List.of(),
                        List.of(),
                        List.of(
                                new com.meetple.backend.domain.push.fcm.PushSendFailure(10L, "UNAVAILABLE"),
                                new com.meetple.backend.domain.push.fcm.PushSendFailure(11L, "UNAVAILABLE"),
                                new com.meetple.backend.domain.push.fcm.PushSendFailure(12L, "UNAVAILABLE")
                        )
                )
        );

        assertThat(List.of(first, second, third))
                .allMatch(delivery -> delivery.getStatus() == PushDeliveryStatus.FAILED);
        verify(pushEventDeliveryRepository, times(1)).findAllByEventIdAndDeviceTokenIdIn(
                org.mockito.ArgumentMatchers.eq(eventId),
                argThat(ids -> ids.size() == 3 && ids.containsAll(List.of(10L, 11L, 12L)))
        );
    }
}
