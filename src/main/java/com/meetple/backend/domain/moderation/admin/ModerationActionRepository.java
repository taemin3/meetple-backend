package com.meetple.backend.domain.moderation.admin;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ModerationActionRepository extends JpaRepository<ModerationAction, Long> {

    boolean existsByReportIdAndActionTypeAndTargetMemberId(
            Long reportId,
            AdminModerationActionType actionType,
            Long targetMemberId
    );

    boolean existsByReportIdAndActionTypeAndTargetMeetingId(
            Long reportId,
            AdminModerationActionType actionType,
            Long targetMeetingId
    );
}
