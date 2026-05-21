package com.meetple.backend.domain.meeting.dto.request;

import jakarta.validation.constraints.Size;

public record CreateMeetingParticipationRequest(
        @Size(max = 500, message = "참여 신청 메시지는 500자 이하여야 합니다.")
        String message
) {
}
