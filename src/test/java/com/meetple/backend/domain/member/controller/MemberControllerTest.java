package com.meetple.backend.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meetple.backend.domain.auth.repository.RefreshTokenRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.global.response.SuccessStatus;
import com.meetple.backend.global.security.JwtTokenProvider;
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

    private String accessToken;
    private String profileImageUrl;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();

        Member member = Member.createUser("user@meetple.com", "encoded-password", "tester", "Seoul");
        Member savedMember = memberRepository.save(member);
        profileImageUrl = "https://cdn.meetple.com/images/profile/" + savedMember.getId()
                + "/550e8400-e29b-41d4-a716-446655440000.png";
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
                .andExpect(jsonPath("$.data.region").value("Seoul"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void updateMyProfileImagePersistsUploadedImageUrl() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/profile-image")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileImageUrl": "%s"
                                }
                                """.formatted(profileImageUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl")
                        .value(profileImageUrl));

        Member savedMember = memberRepository.findByEmail("user@meetple.com").orElseThrow();
        assertThat(savedMember.getProfileImageUrl())
                .isEqualTo(profileImageUrl);
    }

    @Test
    void updateMyProfileImageRejectsExternalUrl() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/profile-image")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileImageUrl": "https://tracker.example/avatar.png"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateMyProfileImageRejectsBlankUrl() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/profile-image")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileImageUrl": " "
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
