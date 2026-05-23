package com.meetple.backend.global.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.auth.repository.AccessTokenBlacklistRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
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

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtAuthenticationEntryPoint authenticationEntryPoint;

    @Mock
    private AccessTokenBlacklistRepository accessTokenBlacklistRepository;

    @Mock
    private FilterChain filterChain;

    @Mock
    private Authentication authentication;

    @Test
    void invalidTokenDoesNotHitBlacklistRepository() throws Exception {
        JwtAuthenticationFilter filter = createFilter();
        MockHttpServletRequest request = protectedRequest("invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        given(jwtTokenProvider.getAuthentication("invalid-token")).willThrow(new JwtException("invalid"));

        filter.doFilter(request, response, filterChain);

        verify(accessTokenBlacklistRepository, never()).exists(any());
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
        given(jwtTokenProvider.getAuthentication("access-token")).willReturn(authentication);
        given(accessTokenBlacklistRepository.exists("access-token")).willReturn(true);

        filter.doFilter(request, response, filterChain);

        InOrder inOrder = inOrder(jwtTokenProvider, accessTokenBlacklistRepository);
        inOrder.verify(jwtTokenProvider).getAuthentication("access-token");
        inOrder.verify(accessTokenBlacklistRepository).exists("access-token");
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
                accessTokenBlacklistRepository
        );
    }

    private MockHttpServletRequest protectedRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }
}
