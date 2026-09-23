package com.meetple.backend.domain.member.dto.response;

import com.meetple.backend.domain.member.entity.Member;

public record PublicMemberProfileResponse(String profileImageUrl, String nickname, String introduction) {
    public static PublicMemberProfileResponse from(Member member, String profileImageUrl) {
        return new PublicMemberProfileResponse(profileImageUrl, member.getNickname(), member.getIntroduction());
    }
}
