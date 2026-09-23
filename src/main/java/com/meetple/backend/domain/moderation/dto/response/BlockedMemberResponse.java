package com.meetple.backend.domain.moderation.dto.response;

import com.meetple.backend.domain.moderation.entity.MemberBlock;
import java.time.LocalDateTime;

public record BlockedMemberResponse(Long memberId, String nickname, String profileImageUrl,
                                    LocalDateTime blockedAt) {
    public static BlockedMemberResponse from(MemberBlock block, String profileImageUrl) {
        return new BlockedMemberResponse(block.getBlocked().getId(), block.getBlocked().getNickname(),
                profileImageUrl, block.getCreatedAt());
    }
}
