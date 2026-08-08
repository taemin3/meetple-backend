package com.meetple.backend.domain.push.fcm;

import java.util.List;

public record PushSendResult(
        List<Long> sentTargetIds,
        List<Long> invalidTargetIds,
        List<PushSendFailure> failures
) {

    public boolean hasFailures() {
        return !failures.isEmpty();
    }
}
