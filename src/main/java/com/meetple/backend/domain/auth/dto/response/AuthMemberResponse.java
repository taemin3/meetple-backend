package com.meetple.backend.domain.auth.dto.response;

import com.meetple.backend.domain.member.entity.Member;

public record AuthMemberResponse(
        Long id,
        String email,
        String nickname,
        String region
) {

    public static AuthMemberResponse from(Member member) {
        return new AuthMemberResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getRegion()
        );
    }
}
