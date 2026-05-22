package com.meetple.backend.domain.member.dto.response;

import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.entity.MemberRole;

public record MemberProfileResponse(
        Long id,
        String email,
        String nickname,
        String profileImageUrl,
        String region,
        MemberRole role
) {

    public static MemberProfileResponse from(Member member) {
        return new MemberProfileResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getProfileImageUrl(),
                member.getRegion(),
                member.getRole()
        );
    }
}
