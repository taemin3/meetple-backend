package com.meetple.backend.domain.push.fcm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import com.meetple.backend.domain.push.service.PushDeviceTarget;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FirebasePushMessageSenderTest {

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @InjectMocks
    private FirebasePushMessageSender firebasePushMessageSender;

    @Test
    void mapsSuccessUnregisteredAndRetryableFailuresByTargetOrder() throws Exception {
        SendResponse success = mock(SendResponse.class);
        given(success.isSuccessful()).willReturn(true);

        FirebaseMessagingException unregisteredException = mock(FirebaseMessagingException.class);
        given(unregisteredException.getMessagingErrorCode()).willReturn(MessagingErrorCode.UNREGISTERED);
        SendResponse unregistered = mock(SendResponse.class);
        given(unregistered.isSuccessful()).willReturn(false);
        given(unregistered.getException()).willReturn(unregisteredException);

        FirebaseMessagingException unavailableException = mock(FirebaseMessagingException.class);
        given(unavailableException.getMessagingErrorCode()).willReturn(MessagingErrorCode.UNAVAILABLE);
        SendResponse unavailable = mock(SendResponse.class);
        given(unavailable.isSuccessful()).willReturn(false);
        given(unavailable.getException()).willReturn(unavailableException);

        BatchResponse batchResponse = mock(BatchResponse.class);
        given(batchResponse.getResponses()).willReturn(List.of(success, unregistered, unavailable));
        given(firebaseMessaging.sendEachForMulticast(any())).willReturn(batchResponse);

        PushSendResult result = firebasePushMessageSender.send(
                message(),
                List.of(
                        new PushDeviceTarget(10L, "token-1"),
                        new PushDeviceTarget(11L, "token-2"),
                        new PushDeviceTarget(12L, "token-3")
                )
        );

        assertThat(result.sentTargetIds()).containsExactly(10L);
        assertThat(result.invalidTargetIds()).containsExactly(11L);
        assertThat(result.invalidTargets()).containsExactly(new InvalidPushTarget(
                11L,
                new PushDeviceTarget(11L, "token-2").tokenHash()
        ));
        assertThat(result.failures()).containsExactly(new PushSendFailure(12L, "UNAVAILABLE"));
    }

    @Test
    void splitsMoreThanFiveHundredTargetsIntoMultipleFcmRequests() throws Exception {
        SendResponse success = mock(SendResponse.class);
        given(success.isSuccessful()).willReturn(true);
        BatchResponse first = mock(BatchResponse.class);
        BatchResponse second = mock(BatchResponse.class);
        given(first.getResponses()).willReturn(java.util.Collections.nCopies(500, success));
        given(second.getResponses()).willReturn(List.of(success));
        given(firebaseMessaging.sendEachForMulticast(any())).willReturn(first, second);
        List<PushDeviceTarget> targets = IntStream.rangeClosed(1, 501)
                .mapToObj(index -> new PushDeviceTarget((long) index, "token-" + index))
                .toList();

        PushSendResult result = firebasePushMessageSender.send(message(), targets);

        assertThat(result.sentTargetIds()).hasSize(501);
        verify(firebaseMessaging, times(2)).sendEachForMulticast(any());
    }

    @Test
    void exposesWholeBatchFcmFailureForKafkaRedelivery() throws Exception {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        given(exception.getMessagingErrorCode()).willReturn(MessagingErrorCode.UNAVAILABLE);
        given(firebaseMessaging.sendEachForMulticast(any())).willThrow(exception);

        assertThatThrownBy(() -> firebasePushMessageSender.send(
                message(),
                List.of(new PushDeviceTarget(10L, "token-1"))
        ))
                .isInstanceOf(PushSendException.class)
                .hasMessageContaining("UNAVAILABLE");
    }

    @Test
    void preservesEarlierBatchSuccessWhenLaterBatchFails() throws Exception {
        SendResponse success = mock(SendResponse.class);
        given(success.isSuccessful()).willReturn(true);
        BatchResponse first = mock(BatchResponse.class);
        given(first.getResponses()).willReturn(java.util.Collections.nCopies(500, success));
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        given(exception.getMessagingErrorCode()).willReturn(MessagingErrorCode.UNAVAILABLE);
        given(firebaseMessaging.sendEachForMulticast(any())).willReturn(first).willThrow(exception);
        List<PushDeviceTarget> targets = IntStream.rangeClosed(1, 501)
                .mapToObj(index -> new PushDeviceTarget((long) index, "token-" + index))
                .toList();

        PushSendException thrown = catchThrowableOfType(
                () -> firebasePushMessageSender.send(message(), targets),
                PushSendException.class
        );

        assertThat(thrown.getPartialResult().sentTargetIds())
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 500)
                        .mapToObj(Long::valueOf)
                        .toList());
        assertThat(thrown.getPartialResult().invalidTargetIds()).isEmpty();
        assertThat(thrown.getPartialResult().failures())
                .containsExactly(new PushSendFailure(501L, "UNAVAILABLE"));
        verify(firebaseMessaging, times(2)).sendEachForMulticast(any());
    }

    private PushMessage message() {
        return new PushMessage(
                "title",
                "body",
                Map.of("eventId", "event-id"),
                "chat-room-1",
                "chat-room-1"
        );
    }
}
