package com.meetple.backend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.auth.repository.AccessTokenBlacklistRepository;
import com.meetple.backend.domain.auth.repository.RefreshTokenRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtAuthenticationEntryPoint authenticationEntryPoint;

    @Mock
    private AccessTokenBlacklistRepository accessTokenBlacklistRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private FilterChain filterChain;

    @Mock
    private Authentication authentication;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeSessionUsesSingleParsedTokenAndContinuesFilterChain() throws Exception {
        JwtAuthenticationFilter filter = createFilter();
        MockHttpServletRequest request = protectedRequest("access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        given(jwtTokenProvider.authenticateAccessToken("access-token"))
                .willReturn(new AuthenticatedAccessToken(
                        authentication,
                        new JwtTokenSession(1L, "session-id")
                ));
        given(accessTokenBlacklistRepository.exists("access-token")).willReturn(false);
        given(refreshTokenRepository.existsByMemberIdAndSessionId(1L, "session-id")).willReturn(true);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(authentication);
        verify(jwtTokenProvider).authenticateAccessToken("access-token");
        verify(jwtTokenProvider, never()).getAuthentication(any());
        verify(jwtTokenProvider, never()).getAccessTokenSession(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidTokenDoesNotHitBlacklistRepository() throws Exception {
        JwtAuthenticationFilter filter = createFilter();
        MockHttpServletRequest request = protectedRequest("invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        given(jwtTokenProvider.authenticateAccessToken("invalid-token"))
                .willThrow(new JwtException("invalid"));

        filter.doFilter(request, response, filterChain);

        verify(accessTokenBlacklistRepository, never()).exists(any());
        verify(refreshTokenRepository, never()).existsByMemberIdAndSessionId(any(), any());
        verify(authenticationEntryPoint).commence(
                eq(request),
                eq(response),
                any(BadCredentialsException.class)
        );
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void blacklistRepositoryIsCheckedAfterTokenValidation() throws Exception {
        JwtAuthenticationFilter filter = createFilter();
        MockHttpServletRequest request = protectedRequest("access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        given(jwtTokenProvider.authenticateAccessToken("access-token"))
                .willReturn(new AuthenticatedAccessToken(
                        authentication,
                        new JwtTokenSession(1L, "session-id")
                ));
        given(accessTokenBlacklistRepository.exists("access-token")).willReturn(true);

        filter.doFilter(request, response, filterChain);

        InOrder inOrder = inOrder(jwtTokenProvider, accessTokenBlacklistRepository);
        inOrder.verify(jwtTokenProvider).authenticateAccessToken("access-token");
        inOrder.verify(accessTokenBlacklistRepository).exists("access-token");
        verify(refreshTokenRepository, never()).existsByMemberIdAndSessionId(any(), any());
        verify(authenticationEntryPoint).commence(
                eq(request),
                eq(response),
                any(BadCredentialsException.class)
        );
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void inactiveSessionReturnsInvalidTokenApiResponse() throws Exception {
        JwtAuthenticationFilter filter = createFilter();
        MockHttpServletRequest request = protectedRequest("access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        given(jwtTokenProvider.authenticateAccessToken("access-token"))
                .willReturn(new AuthenticatedAccessToken(
                        authentication,
                        new JwtTokenSession(1L, "session-id")
                ));
        given(accessTokenBlacklistRepository.exists("access-token")).willReturn(false);
        given(refreshTokenRepository.existsByMemberIdAndSessionId(1L, "session-id")).willReturn(false);

        filter.doFilter(request, response, filterChain);

        verify(authenticationEntryPoint).commence(
                eq(request),
                eq(response),
                any(BadCredentialsException.class)
        );
        verify(filterChain, never()).doFilter(any(), any());
    }

    private JwtAuthenticationFilter createFilter() {
        return new JwtAuthenticationFilter(
                jwtTokenProvider,
                authenticationEntryPoint,
                accessTokenBlacklistRepository,
                refreshTokenRepository
        );
    }

    private MockHttpServletRequest protectedRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }
}
