package com.meetple.backend.global.security;

import com.meetple.backend.domain.auth.repository.AccessTokenValidationRepository;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final AccessTokenValidationRepository accessTokenValidationRepository;
    private final List<RequestMatcher> permitAllRequestMatchers;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            JwtAuthenticationEntryPoint authenticationEntryPoint,
            AccessTokenValidationRepository accessTokenValidationRepository,
            String... permitAllPatterns
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessTokenValidationRepository = accessTokenValidationRepository;
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
            AuthenticatedAccessToken authenticatedToken = jwtTokenProvider.authenticateAccessToken(token);
            JwtTokenSession tokenSession = authenticatedToken.session();
            AccessTokenValidationRepository.Status status = accessTokenValidationRepository.getStatus(
                    token,
                    tokenSession.memberId(),
                    tokenSession.sessionId()
            );
            validateStatus(status);
            SecurityContextHolder.getContext().setAuthentication(authenticatedToken.authentication());
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

    private void validateStatus(AccessTokenValidationRepository.Status status) {
        switch (status) {
            case ACTIVE -> {
            }
            case BLACKLISTED -> throw new IllegalArgumentException("Blacklisted access token.");
            case INACTIVE_SESSION -> throw new IllegalArgumentException("Inactive access token session.");
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith(BEARER_PREFIX)) {
            return authorizationHeader.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
