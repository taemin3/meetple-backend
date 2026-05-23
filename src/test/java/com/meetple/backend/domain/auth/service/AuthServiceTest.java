package com.meetple.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.auth.dto.request.LoginRequest;
import com.meetple.backend.domain.auth.dto.request.LogoutRequest;
import com.meetple.backend.domain.auth.dto.request.ReissueRequest;
import com.meetple.backend.domain.auth.dto.request.SignupRequest;
import com.meetple.backend.domain.auth.dto.response.AuthMemberResponse;
import com.meetple.backend.domain.auth.dto.response.LoginResponse;
import com.meetple.backend.domain.auth.repository.AccessTokenBlacklistRepository;
import com.meetple.backend.domain.auth.repository.RefreshTokenRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.ConflictException;
import com.meetple.backend.global.exception.UnauthorizedException;
import com.meetple.backend.global.response.ErrorStatus;
import com.meetple.backend.global.security.JwtTokenProvider;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AccessTokenBlacklistRepository accessTokenBlacklistRepository;

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
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.findByEmail(request.email())).willReturn(Optional.of(member));
        given(passwordEncoder.matches(request.password(), member.getPassword())).willReturn(true);
        given(jwtTokenProvider.createAccessToken(member)).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(member)).willReturn("refresh-token");
        given(jwtTokenProvider.getAccessTokenExpirationSeconds()).willReturn(3600L);
        given(jwtTokenProvider.getRefreshTokenExpirationSeconds()).willReturn(1209600L);

        LoginResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessTokenExpiresIn()).isEqualTo(3600L);
        assertThat(response.refreshTokenExpiresIn()).isEqualTo(1209600L);
        verify(refreshTokenRepository).save(1L, "refresh-token", Duration.ofSeconds(1209600L));
    }

    @Test
    void reissueRotatesRefreshTokenWhenStoredTokenMatches() {
        ReissueRequest request = new ReissueRequest("old-refresh-token");
        Member member = Member.createUser("user@meetple.com", "encoded-password", "tester", null);
        ReflectionTestUtils.setField(member, "id", 1L);
        given(jwtTokenProvider.getRefreshTokenMemberId(request.refreshToken())).willReturn(1L);
        given(refreshTokenRepository.matches(1L, request.refreshToken())).willReturn(true);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(jwtTokenProvider.createAccessToken(member)).willReturn("new-access-token");
        given(jwtTokenProvider.createRefreshToken(member)).willReturn("new-refresh-token");
        given(jwtTokenProvider.getAccessTokenExpirationSeconds()).willReturn(3600L);
        given(jwtTokenProvider.getRefreshTokenExpirationSeconds()).willReturn(1209600L);

        LoginResponse response = authService.reissue(request);

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        verify(refreshTokenRepository).save(1L, "new-refresh-token", Duration.ofSeconds(1209600L));
    }

    @Test
    void reissueRejectsMismatchedStoredRefreshToken() {
        ReissueRequest request = new ReissueRequest("request-refresh-token");
        given(jwtTokenProvider.getRefreshTokenMemberId(request.refreshToken())).willReturn(1L);
        given(refreshTokenRepository.matches(1L, request.refreshToken())).willReturn(false);

        assertThatThrownBy(() -> authService.reissue(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 refresh token입니다.");

        verify(memberRepository, never()).findById(any());
    }

    @Test
    void reissueRejectsInvalidRefreshToken() {
        ReissueRequest request = new ReissueRequest("invalid-refresh-token");
        given(jwtTokenProvider.getRefreshTokenMemberId(request.refreshToken()))
                .willThrow(new IllegalArgumentException("invalid"));

        assertThatThrownBy(() -> authService.reissue(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 refresh token입니다.");
    }

    @Test
    void logoutDeletesStoredRefreshToken() {
        LogoutRequest request = new LogoutRequest("refresh-token");
        given(jwtTokenProvider.getRefreshTokenMemberId(request.refreshToken())).willReturn(1L);
        given(jwtTokenProvider.getAccessTokenMemberId("access-token")).willReturn(1L);
        given(refreshTokenRepository.matches(1L, request.refreshToken())).willReturn(true);
        given(jwtTokenProvider.getAccessTokenRemainingExpiration("access-token"))
                .willReturn(Duration.ofMinutes(10));

        authService.logout(request, "Bearer access-token");

        InOrder inOrder = inOrder(accessTokenBlacklistRepository, refreshTokenRepository);
        inOrder.verify(accessTokenBlacklistRepository).save("access-token", Duration.ofMinutes(10));
        inOrder.verify(refreshTokenRepository).deleteByMemberId(1L);
    }

    @Test
    void logoutKeepsRefreshTokenWhenAccessTokenBlacklistSaveFails() {
        LogoutRequest request = new LogoutRequest("refresh-token");
        given(jwtTokenProvider.getRefreshTokenMemberId(request.refreshToken())).willReturn(1L);
        given(jwtTokenProvider.getAccessTokenMemberId("access-token")).willReturn(1L);
        given(refreshTokenRepository.matches(1L, request.refreshToken())).willReturn(true);
        given(jwtTokenProvider.getAccessTokenRemainingExpiration("access-token"))
                .willReturn(Duration.ofMinutes(10));
        doThrow(new IllegalStateException("redis timeout"))
                .when(accessTokenBlacklistRepository).save("access-token", Duration.ofMinutes(10));

        assertThatThrownBy(() -> authService.logout(request, "Bearer access-token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis timeout");

        verify(refreshTokenRepository, never()).deleteByMemberId(any());
    }

    @Test
    void logoutRejectsMismatchedAccessTokenMember() {
        LogoutRequest request = new LogoutRequest("refresh-token");
        given(jwtTokenProvider.getRefreshTokenMemberId(request.refreshToken())).willReturn(1L);
        given(jwtTokenProvider.getAccessTokenMemberId("access-token")).willReturn(2L);

        assertThatThrownBy(() -> authService.logout(request, "Bearer access-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 access token입니다.");

        verify(refreshTokenRepository, never()).deleteByMemberId(any());
        verify(accessTokenBlacklistRepository, never()).save(any(), any());
    }

    @Test
    void logoutRejectsMalformedAuthorizationHeader() {
        LogoutRequest request = new LogoutRequest("refresh-token");
        given(jwtTokenProvider.getRefreshTokenMemberId(request.refreshToken())).willReturn(1L);

        assertThatThrownBy(() -> authService.logout(request, "access-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 access token입니다.");

        verify(refreshTokenRepository, never()).deleteByMemberId(any());
        verify(accessTokenBlacklistRepository, never()).save(any(), any());
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
