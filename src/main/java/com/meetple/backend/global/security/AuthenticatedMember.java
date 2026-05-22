package com.meetple.backend.global.security;

import com.meetple.backend.domain.member.entity.MemberRole;

public record AuthenticatedMember(
        Long id,
        String email,
        MemberRole role
) {
}
