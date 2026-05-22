package com.meetple.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.auth.dto.request.LoginRequest;
import com.meetple.backend.domain.auth.dto.request.SignupRequest;
import com.meetple.backend.domain.auth.dto.response.AuthMemberResponse;
import com.meetple.backend.domain.auth.dto.response.LoginResponse;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.ConflictException;
import com.meetple.backend.global.exception.UnauthorizedException;
import com.meetple.backend.global.response.ErrorStatus;
import com.meetple.backend.global.security.JwtTokenProvider;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    void signupEncodesPasswordAndSavesMember() {
        SignupRequest request = new SignupRequest("user@meetple.com", "password123", "tester");
        given(memberRepository.existsByEmail(request.email())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encoded-password");
        given(memberRepository.saveAndFlush(any(Member.class))).willAnswer(invocation -> invocation.getArgument(0));

        AuthMemberResponse response = authService.signup(request);

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).saveAndFlush(memberCaptor.capture());
        Member savedMember = memberCaptor.getValue();

        assertThat(savedMember.getEmail()).isEqualTo(request.email());
        assertThat(savedMember.getPassword()).isEqualTo("encoded-password");
        assertThat(savedMember.getNickname()).isEqualTo(request.nickname());
        assertThat(response.email()).isEqualTo(request.email());
        assertThat(response.nickname()).isEqualTo(request.nickname());
    }

    @Test
    void signupRejectsDuplicateEmail() {
        SignupRequest request = new SignupRequest("user@meetple.com", "password123", "tester");
        given(memberRepository.existsByEmail(request.email())).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage(ErrorStatus.EMAIL_ALREADY_EXISTS.getMessage());

        verify(passwordEncoder, never()).encode(any());
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    void signupMapsUniqueConstraintViolationToDuplicateEmail() {
        SignupRequest request = new SignupRequest("user@meetple.com", "password123", "tester");
        given(memberRepository.existsByEmail(request.email())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encoded-password");
        given(memberRepository.saveAndFlush(any(Member.class)))
                .willThrow(new DataIntegrityViolationException("uk_members_email"));

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage(ErrorStatus.EMAIL_ALREADY_EXISTS.getMessage());
    }

    @Test
    void signupRejectsPasswordOverBcryptByteLimit() {
        SignupRequest request = new SignupRequest("user@meetple.com", "가".repeat(25), "tester");

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");

        verify(memberRepository, never()).existsByEmail(any());
    }

    @Test
    void loginReturnsAccessTokenWhenPasswordMatches() {
        LoginRequest request = new LoginRequest("user@meetple.com", "password123");
        Member member = Member.createUser(request.email(), "encoded-password", "tester", null);
        given(memberRepository.findByEmail(request.email())).willReturn(Optional.of(member));
        given(passwordEncoder.matches(request.password(), member.getPassword())).willReturn(true);
        given(jwtTokenProvider.createAccessToken(member)).willReturn("access-token");
        given(jwtTokenProvider.getAccessTokenExpirationSeconds()).willReturn(3600L);

        LoginResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600L);
    }

    @Test
    void loginRejectsUnknownEmail() {
        LoginRequest request = new LoginRequest("user@meetple.com", "password123");
        given(memberRepository.findByEmail(request.email())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    @Test
    void loginRejectsWrongPassword() {
        LoginRequest request = new LoginRequest("user@meetple.com", "password123");
        Member member = Member.createUser(request.email(), "encoded-password", "tester", null);
        given(memberRepository.findByEmail(request.email())).willReturn(Optional.of(member));
        given(passwordEncoder.matches(request.password(), member.getPassword())).willReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    @Test
    void loginRejectsPasswordOverBcryptByteLimit() {
        LoginRequest request = new LoginRequest("user@meetple.com", "가".repeat(25));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");

        verify(memberRepository, never()).findByEmail(any());
    }
}
