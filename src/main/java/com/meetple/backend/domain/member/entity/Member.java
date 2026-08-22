package com.meetple.backend.domain.member.entity;

import com.meetple.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

@Getter
@Entity
@DynamicUpdate
@Table(
        name = "members",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_members_email", columnNames = "email")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "profile_image_object_key", length = 255)
    private String profileImageObjectKey;

    @Column(length = 30)
    private String introduction;

    @Column(length = 100)
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MemberRole role;

    private Member(String email, String password, String nickname, String region, MemberRole role) {
        this.email = email;
        this.password = password;
        this.emailVerifiedAt = LocalDateTime.now();
        this.nickname = nickname;
        this.region = region;
        this.role = role;
    }

    public static Member createUser(String email, String password, String nickname, String region) {
        return new Member(email, password, nickname, region, MemberRole.USER);
    }

    public void updateProfileImage(String profileImageObjectKey) {
        this.profileImageObjectKey = profileImageObjectKey;
    }

    public void deleteProfileImage() {
        this.profileImageObjectKey = null;
    }

    public void updateProfile(String nickname, String introduction) {
        this.nickname = nickname;
        this.introduction = introduction;
    }
}
