package com.meetple.backend.domain.moderation.dto.request;

import com.meetple.backend.domain.moderation.entity.ReportReason;
import com.meetple.backend.domain.moderation.entity.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
        @NotNull ReportTargetType targetType,
        @NotNull @Positive Long targetId,
        @NotNull ReportReason reason,
        @Size(max = 500) String otherDescription
) {
}
