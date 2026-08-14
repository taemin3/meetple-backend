package com.meetple.backend.domain.meeting.dto.response;

import com.meetple.backend.domain.member.entity.Member;

public record MeetingMemberResponse(
        Long memberId,
        String nickname,
        String profileImageUrl,
        boolean host
) {
    public static MeetingMemberResponse host(Member member, String profileImageUrl) {
        return new MeetingMemberResponse(
                member.getId(),
                member.getNickname(),
                profileImageUrl,
                true
        );
    }

    public static MeetingMemberResponse participant(Member member, String profileImageUrl) {
        return new MeetingMemberResponse(
                member.getId(),
                member.getNickname(),
                profileImageUrl,
                false
        );
    }
}
