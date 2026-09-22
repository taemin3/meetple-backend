package com.meetple.backend.domain.moderation.entity;

import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "member_blocks", uniqueConstraints = @UniqueConstraint(
        name = "uk_member_blocks_relationship",
        columnNames = {"blocker_member_id", "blocked_member_id"}
))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberBlock extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_member_id", nullable = false)
    private Member blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_member_id", nullable = false)
    private Member blocked;

    private MemberBlock(Member blocker, Member blocked) {
        this.blocker = blocker;
        this.blocked = blocked;
    }

    public static MemberBlock create(Member blocker, Member blocked) {
        return new MemberBlock(blocker, blocked);
    }
}
