package com.meetple.backend.domain.push.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetple.backend.domain.chat.service.ChatNotificationSettingService;
import com.meetple.backend.domain.push.delivery.PushDeliveryService;
import com.meetple.backend.domain.push.delivery.PushDeliveryClaim;
import com.meetple.backend.domain.push.fcm.InvalidPushTarget;
import com.meetple.backend.domain.push.fcm.PushMessage;
import com.meetple.backend.domain.push.fcm.PushMessageSender;
import com.meetple.backend.domain.push.fcm.PushSendException;
import com.meetple.backend.domain.push.fcm.PushSendFailure;
import com.meetple.backend.domain.push.fcm.PushSendResult;
import com.meetple.backend.domain.push.service.PushDeviceTarget;
import com.meetple.backend.domain.push.service.PushDeviceTokenService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushEventProcessorTest {

    private static final String NOTIFICATION_TOPIC = "meetple.push.notification.v1";
    private static final String CHAT_TOPIC = "meetple.push.chat.v1";
    private static final UUID CLAIM_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    private PushDeviceTokenService pushDeviceTokenService;

    @Mock
    private PushDeliveryService pushDeliveryService;

    @Mock
    private PushMessageSender pushMessageSender;

    @Mock
    private ChatNotificationSettingService chatNotificationSettingService;

    private PushEventProcessor pushEventProcessor;

    @BeforeEach
    void setUp() {
        pushEventProcessor = new PushEventProcessor(
                new ObjectMapper(),
                pushDeviceTokenService,
                pushDeliveryService,
                pushMessageSender,
                chatNotificationSettingService
        );
    }

    @Test
    void sendsGeneralNotificationToEveryRegisteredDevice() {
        UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        List<PushDeviceTarget> targets = List.of(
                new PushDeviceTarget(10L, "token-1"),
                new PushDeviceTarget(11L, "token-2")
        );
        given(pushDeviceTokenService.findTargets(List.of(7L))).willReturn(targets);
        given(pushDeliveryService.prepare(eventId, targets))
                .willReturn(new PushDeliveryClaim(CLAIM_ID, targets, false));
        given(pushMessageSender.send(any(PushMessage.class), eq(targets)))
                .willReturn(new PushSendResult(List.of(10L, 11L), List.of(), List.of()));

        pushEventProcessor.process(NOTIFICATION_TOPIC, generalPayload(eventId));

        ArgumentCaptor<PushMessage> messageCaptor = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushMessageSender).send(messageCaptor.capture(), eq(targets));
        PushMessage message = messageCaptor.getValue();
        assertThat(message.title()).isEqualTo("참여가 승인됐어요");
        assertThat(message.body()).isEqualTo("모임 참여 신청이 승인됐습니다.");
        assertThat(message.data()).containsEntry("route", "MEETING_DETAIL");
        assertThat(message.data()).containsEntry("meetingId", "101");
        assertThat(message.data()).containsEntry("eventId", eventId.toString());
        verify(pushDeliveryService).record(
                eventId,
                CLAIM_ID,
                new PushSendResult(List.of(10L, 11L), List.of(), List.of())
        );
    }

    @Test
    void excludesChatSenderAndUsesRoomGroupingKey() {
        UUID eventId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        List<PushDeviceTarget> targets = List.of(new PushDeviceTarget(20L, "token-2"));
        given(chatNotificationSettingService.filterPushEnabledRecipients(
                eq(55L),
                argThat(ids -> ids.size() == 2 && ids.containsAll(List.of(2L, 3L)))
        )).willReturn(List.of(2L, 3L));
        given(pushDeviceTokenService.findTargets(argThat(ids ->
                ids.size() == 2 && ids.containsAll(List.of(2L, 3L))
        ))).willReturn(targets);
        given(pushDeliveryService.prepare(eventId, targets))
                .willReturn(new PushDeliveryClaim(CLAIM_ID, targets, false));
        InvalidPushTarget invalidTarget = new InvalidPushTarget(20L, targets.getFirst().tokenHash());
        given(pushMessageSender.send(any(PushMessage.class), eq(targets)))
                .willReturn(new PushSendResult(List.of(), List.of(invalidTarget), List.of()));

        pushEventProcessor.process(CHAT_TOPIC, chatPayload(eventId));

        ArgumentCaptor<PushMessage> messageCaptor = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushMessageSender).send(messageCaptor.capture(), eq(targets));
        PushMessage message = messageCaptor.getValue();
        assertThat(message.collapseKey()).isEqualTo("chat-room-55");
        assertThat(message.notificationTag()).isEqualTo("chat-room-55");
        assertThat(message.data()).containsEntry("route", "CHAT_ROOM");
        assertThat(message.data()).containsEntry("roomId", "55");
        verify(pushDeviceTokenService).removeInvalidTargets(List.of(invalidTarget));
    }

    @Test
    void excludesMembersWhoDisabledThisChatRoomPush() {
        UUID eventId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        given(chatNotificationSettingService.filterPushEnabledRecipients(
                eq(55L),
                argThat(ids -> ids.size() == 2 && ids.containsAll(List.of(2L, 3L)))
        )).willReturn(List.of(3L));
        given(pushDeviceTokenService.findTargets(List.of(3L))).willReturn(List.of());
        given(pushDeliveryService.prepare(eventId, List.of()))
                .willReturn(PushDeliveryClaim.empty());

        pushEventProcessor.process(CHAT_TOPIC, chatPayload(eventId));

        verify(pushDeviceTokenService).findTargets(List.of(3L));
        verify(pushMessageSender, never()).send(any(), any());
    }

    @Test
    void skipsFcmWhenDuplicateEventHasNoPendingTarget() {
        UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        List<PushDeviceTarget> targets = List.of(new PushDeviceTarget(10L, "token-1"));
        given(pushDeviceTokenService.findTargets(List.of(7L))).willReturn(targets);
        given(pushDeliveryService.prepare(eventId, targets)).willReturn(PushDeliveryClaim.empty());

        pushEventProcessor.process(NOTIFICATION_TOPIC, generalPayload(eventId));

        verify(pushMessageSender, never()).send(any(), any());
    }

    @Test
    void recordsPartialBatchResultBeforeRethrowing() {
        UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        List<PushDeviceTarget> targets = List.of(
                new PushDeviceTarget(10L, "token-1"),
                new PushDeviceTarget(11L, "token-2"),
                new PushDeviceTarget(12L, "token-3")
        );
        given(pushDeviceTokenService.findTargets(List.of(7L))).willReturn(targets);
        given(pushDeliveryService.prepare(eventId, targets))
                .willReturn(new PushDeliveryClaim(CLAIM_ID, targets, false));
        InvalidPushTarget invalidTarget = new InvalidPushTarget(11L, targets.get(1).tokenHash());
        PushSendResult partialResult = new PushSendResult(
                List.of(10L),
                List.of(invalidTarget),
                List.of(new PushSendFailure(12L, "UNAVAILABLE"))
        );
        PushSendException failure = new PushSendException(
                "UNAVAILABLE",
                partialResult,
                new RuntimeException()
        );
        given(pushMessageSender.send(any(PushMessage.class), eq(targets))).willThrow(failure);

        assertThatThrownBy(() -> pushEventProcessor.process(NOTIFICATION_TOPIC, generalPayload(eventId)))
                .isSameAs(failure);

        verify(pushDeliveryService).record(eventId, CLAIM_ID, partialResult);
        verify(pushDeviceTokenService).removeInvalidTargets(List.of(invalidTarget));
    }

    @Test
    void rejectsUnsupportedSchemaVersionBeforeLookingUpTokens() {
        String payload = """
                {
                  "eventId": "33333333-3333-3333-3333-333333333333",
                  "eventType": "PARTICIPATION_APPROVED",
                  "schemaVersion": 2,
                  "occurredAt": "2026-08-08T00:00:00Z",
                  "aggregateType": "notification",
                  "aggregateId": "1",
                  "data": {}
                }
                """;

        assertThatThrownBy(() -> pushEventProcessor.process(NOTIFICATION_TOPIC, payload))
                .isInstanceOf(NonRetryablePushEventException.class)
                .hasMessageContaining("Unsupported push event schema version");

        verify(pushDeviceTokenService, never()).findTargets(any());
    }

    @Test
    void throwsAfterRecordingPerTargetFailureSoKafkaCanRedeliver() {
        UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        List<PushDeviceTarget> targets = List.of(new PushDeviceTarget(10L, "token-1"));
        PushSendResult result = new PushSendResult(
                List.of(),
                List.of(),
                List.of(new PushSendFailure(10L, "UNAVAILABLE"))
        );
        given(pushDeviceTokenService.findTargets(List.of(7L))).willReturn(targets);
        given(pushDeliveryService.prepare(eventId, targets))
                .willReturn(new PushDeliveryClaim(CLAIM_ID, targets, false));
        given(pushMessageSender.send(any(PushMessage.class), eq(targets))).willReturn(result);

        assertThatThrownBy(() -> pushEventProcessor.process(NOTIFICATION_TOPIC, generalPayload(eventId)))
                .isInstanceOf(PushEventProcessingException.class)
                .hasMessageContaining("1 target");

        verify(pushDeliveryService).record(eventId, CLAIM_ID, result);
    }

    @Test
    void retriesKafkaRecordWhenAnotherConsumerOwnsAnActiveClaim() {
        UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        List<PushDeviceTarget> targets = List.of(new PushDeviceTarget(10L, "token-1"));
        given(pushDeviceTokenService.findTargets(List.of(7L))).willReturn(targets);
        given(pushDeliveryService.prepare(eventId, targets)).willReturn(PushDeliveryClaim.blocked());

        assertThatThrownBy(() -> pushEventProcessor.process(NOTIFICATION_TOPIC, generalPayload(eventId)))
                .isInstanceOf(PushEventProcessingException.class)
                .hasMessageContaining("already being processed");

        verify(pushMessageSender, never()).send(any(), any());
    }

    private String generalPayload(UUID eventId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "PARTICIPATION_APPROVED",
                  "schemaVersion": 1,
                  "occurredAt": "2026-08-08T00:00:00Z",
                  "aggregateType": "notification",
                  "aggregateId": "501",
                  "data": {
                    "recipientMemberId": 7,
                    "notificationId": 501,
                    "meetingId": 101,
                    "title": "참여가 승인됐어요",
                    "body": "모임 참여 신청이 승인됐습니다."
                  }
                }
                """.formatted(eventId);
    }

    private String chatPayload(UUID eventId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "CHAT_MESSAGE_CREATED",
                  "schemaVersion": 1,
                  "occurredAt": "2026-08-08T00:00:00Z",
                  "aggregateType": "chatMessage",
                  "aggregateId": "9001",
                  "data": {
                    "recipientMemberIds": [1, 2, 3],
                    "senderMemberId": 1,
                    "senderNickname": "보낸 사람",
                    "roomId": 55,
                    "chatMessageId": 9001,
                    "roomSequence": 30,
                    "title": "러닝 모임",
                    "body": "곧 도착합니다."
                  }
                }
                """.formatted(eventId);
    }
}
