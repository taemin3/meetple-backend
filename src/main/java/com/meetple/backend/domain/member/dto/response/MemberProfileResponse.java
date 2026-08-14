package com.meetple.backend.domain.member.dto.response;

import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.entity.MemberRole;

public record MemberProfileResponse(
        Long id,
        String email,
        String nickname,
        String profileImageUrl,
        String region,
        MemberRole role,
        long createdMeetingsCount,
        long joinedMeetingsCount,
        long likedMeetingsCount
) {

    public static MemberProfileResponse from(
            Member member,
            String profileImageUrl,
            long createdMeetingsCount,
            long joinedMeetingsCount,
            long likedMeetingsCount
    ) {
        return new MemberProfileResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                profileImageUrl,
                member.getRegion(),
                member.getRole(),
                createdMeetingsCount,
                joinedMeetingsCount,
                likedMeetingsCount
        );
    }
}
