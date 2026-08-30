package com.meetple.backend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.auth.repository.AccessTokenValidationRepository;
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
    private AccessTokenValidationRepository accessTokenValidationRepository;

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
        given(accessTokenValidationRepository.getStatus("access-token", 1L, "session-id"))
                .willReturn(AccessTokenValidationRepository.Status.ACTIVE);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(authentication);
        verify(jwtTokenProvider).authenticateAccessToken("access-token");
        verify(jwtTokenProvider, never()).getAuthentication(any());
        verify(jwtTokenProvider, never()).getAccessTokenSession(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidTokenDoesNotHitRedisValidationRepository() throws Exception {
        JwtAuthenticationFilter filter = createFilter();
        MockHttpServletRequest request = protectedRequest("invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        given(jwtTokenProvider.authenticateAccessToken("invalid-token"))
                .willThrow(new JwtException("invalid"));

        filter.doFilter(request, response, filterChain);

        verify(accessTokenValidationRepository, never()).getStatus(any(), any(), any());
        verify(authenticationEntryPoint).commence(
                eq(request),
                eq(response),
                any(BadCredentialsException.class)
        );
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void blacklistStatusIsCheckedAfterTokenValidation() throws Exception {
        JwtAuthenticationFilter filter = createFilter();
        MockHttpServletRequest request = protectedRequest("access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        given(jwtTokenProvider.authenticateAccessToken("access-token"))
                .willReturn(new AuthenticatedAccessToken(
                        authentication,
                        new JwtTokenSession(1L, "session-id")
                ));
        given(accessTokenValidationRepository.getStatus("access-token", 1L, "session-id"))
                .willReturn(AccessTokenValidationRepository.Status.BLACKLISTED);

        filter.doFilter(request, response, filterChain);

        InOrder inOrder = inOrder(jwtTokenProvider, accessTokenValidationRepository);
        inOrder.verify(jwtTokenProvider).authenticateAccessToken("access-token");
        inOrder.verify(accessTokenValidationRepository).getStatus("access-token", 1L, "session-id");
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
        given(accessTokenValidationRepository.getStatus("access-token", 1L, "session-id"))
                .willReturn(AccessTokenValidationRepository.Status.INACTIVE_SESSION);

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
                accessTokenValidationRepository
        );
    }

    private MockHttpServletRequest protectedRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }
}
