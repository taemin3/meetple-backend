package com.meetple.backend.domain.push.fcm;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.meetple.backend.domain.push.service.PushDeviceTarget;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "push.fcm", name = "enabled", havingValue = "true")
public class FirebasePushMessageSender implements PushMessageSender {

    private static final int MAX_MULTICAST_TARGETS = 500;
    private static final String UNKNOWN_ERROR = "UNKNOWN";

    private final FirebaseMessaging firebaseMessaging;

    @Override
    public PushSendResult send(PushMessage message, List<PushDeviceTarget> targets) {
        List<Long> sentTargetIds = new ArrayList<>();
        List<InvalidPushTarget> invalidTargets = new ArrayList<>();
        List<PushSendFailure> failures = new ArrayList<>();

        for (int start = 0; start < targets.size(); start += MAX_MULTICAST_TARGETS) {
            int end = Math.min(start + MAX_MULTICAST_TARGETS, targets.size());
            List<PushDeviceTarget> batchTargets = targets.subList(start, end);
            try {
                BatchResponse response = sendBatch(message, batchTargets);
                collectResults(batchTargets, response, sentTargetIds, invalidTargets, failures);
            } catch (FirebaseMessagingException exception) {
                String errorCode = errorCode(exception);
                targets.subList(start, targets.size()).forEach(target ->
                        failures.add(new PushSendFailure(target.deviceTokenId(), errorCode))
                );
                throw new PushSendException(
                        errorCode,
                        new PushSendResult(
                                List.copyOf(sentTargetIds),
                                List.copyOf(invalidTargets),
                                List.copyOf(failures)
                        ),
                        exception
                );
            }
        }

        return new PushSendResult(sentTargetIds, invalidTargets, failures);
    }

    private BatchResponse sendBatch(
            PushMessage message,
            List<PushDeviceTarget> targets
    ) throws FirebaseMessagingException {
        MulticastMessage multicastMessage = MulticastMessage.builder()
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .putAllData(message.data())
                .setAndroidConfig(androidConfig(message))
                .addAllTokens(targets.stream().map(PushDeviceTarget::token).toList())
                .build();
        return firebaseMessaging.sendEachForMulticast(multicastMessage);
    }

    private AndroidConfig androidConfig(PushMessage message) {
        AndroidConfig.Builder config = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH);
        if (StringUtils.hasText(message.collapseKey())) {
            config.setCollapseKey(message.collapseKey());
        }

        AndroidNotification.Builder notification = AndroidNotification.builder()
                .setClickAction("FLUTTER_NOTIFICATION_CLICK");
        if (StringUtils.hasText(message.notificationTag())) {
            notification.setTag(message.notificationTag());
        }
        return config.setNotification(notification.build()).build();
    }

    private void collectResults(
            List<PushDeviceTarget> targets,
            BatchResponse response,
            List<Long> sentTargetIds,
            List<InvalidPushTarget> invalidTargets,
            List<PushSendFailure> failures
    ) {
        List<SendResponse> responses = response.getResponses();
        for (int index = 0; index < responses.size(); index++) {
            PushDeviceTarget target = targets.get(index);
            SendResponse sendResponse = responses.get(index);
            if (sendResponse.isSuccessful()) {
                sentTargetIds.add(target.deviceTokenId());
                continue;
            }

            FirebaseMessagingException exception = sendResponse.getException();
            String errorCode = exception == null ? UNKNOWN_ERROR : errorCode(exception);
            if (exception != null && exception.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                invalidTargets.add(new InvalidPushTarget(
                        target.deviceTokenId(),
                        target.tokenHash()
                ));
            } else {
                failures.add(new PushSendFailure(target.deviceTokenId(), errorCode));
            }
        }
    }

    private String errorCode(FirebaseMessagingException exception) {
        MessagingErrorCode messagingErrorCode = exception.getMessagingErrorCode();
        return messagingErrorCode == null ? UNKNOWN_ERROR : messagingErrorCode.name();
    }
}
