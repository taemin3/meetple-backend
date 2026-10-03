package com.meetple.backend.domain.moderation.admin;

import java.time.Duration;
import java.util.Optional;

public enum AdminModerationActionType {
    DISMISS,
    WARNING,
    SUSPEND_1_DAY(Duration.ofDays(1)),
    SUSPEND_3_DAYS(Duration.ofDays(3)),
    SUSPEND_7_DAYS(Duration.ofDays(7)),
    PERMANENT_SUSPENSION,
    FORCE_DELETE_MEETING,
    RELEASE_SUSPENSION,
    RESTORE_MEETING;

    private final Duration suspensionDuration;

    AdminModerationActionType() {
        this.suspensionDuration = null;
    }

    AdminModerationActionType(Duration suspensionDuration) {
        this.suspensionDuration = suspensionDuration;
    }

    public Optional<Duration> suspensionDuration() {
        return Optional.ofNullable(suspensionDuration);
    }

    public boolean isInitialResolution() {
        return this != RELEASE_SUSPENSION && this != RESTORE_MEETING;
    }

    public boolean targetsMember() {
        return this == WARNING
                || suspensionDuration != null
                || this == PERMANENT_SUSPENSION
                || this == RELEASE_SUSPENSION;
    }

    public boolean targetsMeeting() {
        return this == FORCE_DELETE_MEETING || this == RESTORE_MEETING;
    }

    public boolean isSuspension() {
        return suspensionDuration != null || this == PERMANENT_SUSPENSION;
    }
}
