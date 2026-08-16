package com.meetple.backend.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meetple.backend.domain.auth.repository.RefreshTokenRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.global.response.SuccessStatus;
import com.meetple.backend.global.security.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private String accessToken;
    private String profileImageUrl;
    private String profileImageObjectKey;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();

        Member member = Member.createUser("user@meetple.com", "encoded-password", "tester", "Seoul");
        Member savedMember = memberRepository.save(member);
        profileImageObjectKey = "images/profile/" + savedMember.getId()
                + "/550e8400-e29b-41d4-a716-446655440000.png";
        profileImageUrl = "https://cdn.meetple.com/" + profileImageObjectKey;
        accessToken = jwtTokenProvider.createAccessToken(savedMember, "member-controller-test-session");
        refreshTokenRepository.save(
                savedMember.getId(),
                "member-controller-test-session",
                "refresh-token",
                Duration.ofMinutes(10)
        );
    }

    @Test
    void getMyProfileReturnsCurrentMemberProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(SuccessStatus.OK.getCode()))
                .andExpect(jsonPath("$.data.email").value("user@meetple.com"))
                .andExpect(jsonPath("$.data.nickname").value("tester"))
                .andExpect(jsonPath("$.data.introduction").isEmpty())
                .andExpect(jsonPath("$.data.region").value("Seoul"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void updateMyProfilePersistsNicknameAndIntroduction() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": " 모임친구 ",
                                  "introduction": " 같이 산책해요 "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("모임친구"))
                .andExpect(jsonPath("$.data.introduction").value("같이 산책해요"));

        Member savedMember = memberRepository.findByEmail("user@meetple.com").orElseThrow();
        assertThat(savedMember.getNickname()).isEqualTo("모임친구");
        assertThat(savedMember.getIntroduction()).isEqualTo("같이 산책해요");
    }

    @Test
    void updateMyProfileRejectsInvalidFields() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": " ",
                                  "introduction": "1234567890123456789012345678901"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateMyProfileImagePersistsUploadedImageObjectKey() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/profile-image")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileImageObjectKey": "%s"
                                }
                                """.formatted(profileImageObjectKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl")
                        .value(profileImageUrl));

        Member savedMember = memberRepository.findByEmail("user@meetple.com").orElseThrow();
        assertThat(savedMember.getProfileImageObjectKey())
                .isEqualTo(profileImageObjectKey);
    }

    @Test
    void updateMyProfileImageRejectsExternalObjectKey() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/profile-image")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileImageObjectKey": "https://tracker.example/avatar.png"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateMyProfileImageRejectsBlankObjectKey() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/profile-image")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileImageObjectKey": " "
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteMyProfileImageClearsUploadedImageObjectKey() throws Exception {
        Member member = memberRepository.findByEmail("user@meetple.com").orElseThrow();
        member.updateProfileImage(profileImageObjectKey);
        memberRepository.saveAndFlush(member);

        mockMvc.perform(delete("/api/v1/users/me/profile-image")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl").isEmpty());

        Member savedMember = memberRepository.findByEmail("user@meetple.com").orElseThrow();
        assertThat(savedMember.getProfileImageObjectKey()).isNull();
    }

    @Test
    void concurrentProfileUpdatesPreserveDisjointFields() {
        Member member = memberRepository.findByEmail("user@meetple.com").orElseThrow();
        member.updateProfileImage(profileImageObjectKey);
        memberRepository.saveAndFlush(member);

        EntityManager profileEntityManager = entityManagerFactory.createEntityManager();
        EntityManager imageEntityManager = entityManagerFactory.createEntityManager();
        try {
            profileEntityManager.getTransaction().begin();
            Member staleProfile = profileEntityManager.find(Member.class, member.getId());

            imageEntityManager.getTransaction().begin();
            Member imageProfile = imageEntityManager.find(Member.class, member.getId());
            imageProfile.deleteProfileImage();
            imageEntityManager.getTransaction().commit();

            staleProfile.updateProfile("동시수정", "이미지 삭제를 유지해요");
            profileEntityManager.getTransaction().commit();
        } finally {
            rollbackIfActive(profileEntityManager);
            rollbackIfActive(imageEntityManager);
            profileEntityManager.close();
            imageEntityManager.close();
        }

        Member savedMember = memberRepository.findByEmail("user@meetple.com").orElseThrow();
        assertThat(savedMember.getNickname()).isEqualTo("동시수정");
        assertThat(savedMember.getIntroduction()).isEqualTo("이미지 삭제를 유지해요");
        assertThat(savedMember.getProfileImageObjectKey()).isNull();
    }

    private void rollbackIfActive(EntityManager entityManager) {
        if (entityManager.getTransaction().isActive()) {
            entityManager.getTransaction().rollback();
        }
    }
}
