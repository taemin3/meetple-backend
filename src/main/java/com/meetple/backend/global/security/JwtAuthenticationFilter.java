package com.meetple.backend.global.security;

import com.meetple.backend.domain.auth.repository.AccessTokenBlacklistRepository;
import com.meetple.backend.domain.auth.repository.RefreshTokenRepository;
import com.meetple.backend.global.response.ErrorStatus;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final AccessTokenBlacklistRepository accessTokenBlacklistRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final List<RequestMatcher> permitAllRequestMatchers;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            JwtAuthenticationEntryPoint authenticationEntryPoint,
            AccessTokenBlacklistRepository accessTokenBlacklistRepository,
            RefreshTokenRepository refreshTokenRepository,
            String... permitAllPatterns
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessTokenBlacklistRepository = accessTokenBlacklistRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.permitAllRequestMatchers = Arrays.stream(permitAllPatterns)
                .map(PathPatternRequestMatcher::pathPattern)
                .map(RequestMatcher.class::cast)
                .toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return permitAllRequestMatchers.stream()
                .anyMatch(requestMatcher -> requestMatcher.matches(request));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveToken(request);

        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Authentication authentication = jwtTokenProvider.getAuthentication(token);
            if (accessTokenBlacklistRepository.exists(token)) {
                throw new IllegalArgumentException("Blacklisted access token.");
            }
            JwtTokenSession tokenSession = jwtTokenProvider.getAccessTokenSession(token);
            if (!refreshTokenRepository.existsByMemberIdAndSessionId(
                    tokenSession.memberId(),
                    tokenSession.sessionId()
            )) {
                throw new IllegalArgumentException("Inactive access token session.");
            }
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            request.setAttribute(JwtAuthenticationEntryPoint.ERROR_STATUS_ATTRIBUTE, ErrorStatus.INVALID_TOKEN);
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException(ErrorStatus.INVALID_TOKEN.getMessage(), e)
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith(BEARER_PREFIX)) {
            return authorizationHeader.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
