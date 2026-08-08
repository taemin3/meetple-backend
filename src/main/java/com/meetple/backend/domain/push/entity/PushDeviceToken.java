package com.meetple.backend.domain.push.entity;

import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "push_device_tokens",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_push_device_tokens_device_id", columnNames = "device_id"),
                @UniqueConstraint(name = "uk_push_device_tokens_token_hash", columnNames = "token_hash")
        },
        indexes = @Index(name = "idx_push_device_tokens_member", columnList = "member_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushDeviceToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "device_id", nullable = false, length = 100)
    private String deviceId;

    @Column(nullable = false, length = 4096)
    private String token;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PushDevicePlatform platform;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    private PushDeviceToken(
            Member member,
            String deviceId,
            String token,
            String tokenHash,
            PushDevicePlatform platform
    ) {
        refresh(member, deviceId, token, tokenHash, platform);
    }

    public static PushDeviceToken create(
            Member member,
            String deviceId,
            String token,
            String tokenHash,
            PushDevicePlatform platform
    ) {
        return new PushDeviceToken(member, deviceId, token, tokenHash, platform);
    }

    public void refresh(
            Member member,
            String deviceId,
            String token,
            String tokenHash,
            PushDevicePlatform platform
    ) {
        this.member = member;
        this.deviceId = deviceId;
        this.token = token;
        this.tokenHash = tokenHash;
        this.platform = platform;
        this.lastSeenAt = LocalDateTime.now();
    }
}
