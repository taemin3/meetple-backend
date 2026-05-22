package com.meetple.backend.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.meetple.backend.domain.member.dto.response.MemberProfileResponse;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.entity.MemberRole;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.global.exception.NotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MemberService memberService;

    @Test
    void getMyProfileReturnsCurrentMemberProfile() {
        Member member = Member.createUser("user@meetple.com", "encoded-password", "tester", "Seoul");
        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(member, "profileImageUrl", "https://example.com/profile.png");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        MemberProfileResponse response = memberService.getMyProfile(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("user@meetple.com");
        assertThat(response.nickname()).isEqualTo("tester");
        assertThat(response.profileImageUrl()).isEqualTo("https://example.com/profile.png");
        assertThat(response.region()).isEqualTo("Seoul");
        assertThat(response.role()).isEqualTo(MemberRole.USER);
    }

    @Test
    void getMyProfileRejectsUnknownMember() {
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getMyProfile(1L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("회원을 찾을 수 없습니다.");
    }
}
