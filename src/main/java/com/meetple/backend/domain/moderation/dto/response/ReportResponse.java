package com.meetple.backend.domain.moderation.dto.response;

import com.meetple.backend.domain.moderation.entity.Report;
import com.meetple.backend.domain.moderation.entity.ReportReason;
import com.meetple.backend.domain.moderation.entity.ReportTargetType;
import java.time.LocalDateTime;

public record ReportResponse(Long id, ReportTargetType targetType, Long targetId,
                             ReportReason reason, LocalDateTime createdAt) {
    public static ReportResponse from(Report report) {
        return new ReportResponse(report.getId(), report.getTargetType(), report.getTargetId(),
                report.getReason(), report.getCreatedAt());
    }
}
