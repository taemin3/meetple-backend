package com.meetple.backend.domain.push.fcm;

import java.util.List;

public record PushSendResult(
        List<Long> sentTargetIds,
        List<InvalidPushTarget> invalidTargets,
        List<PushSendFailure> failures
) {

    public List<Long> invalidTargetIds() {
        return invalidTargets.stream().map(InvalidPushTarget::targetId).toList();
    }

    public boolean hasFailures() {
        return !failures.isEmpty();
    }
}
