package com.meetple.backend.domain.meeting.dto.response;

import java.util.List;

public record MeetingEngagementResponse(
        boolean host,
        boolean bookmarked,
        MeetingParticipationResponse participation,
        List<MeetingMemberResponse> members
) {
}
