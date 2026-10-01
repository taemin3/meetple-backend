package com.meetple.backend.global.security;

import com.meetple.backend.domain.auth.repository.AccessTokenValidationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/health",
            "/health-data",
            "/health-error",
            "/health-notfound",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/livez",
            "/readyz",
            "/api/v1/auth/signup",
            "/api/v1/auth/email-verifications",
            "/api/v1/auth/email-verifications/confirm",
            "/api/v1/auth/password-resets",
            "/api/v1/auth/password-resets/email-verifications",
            "/api/v1/auth/password-resets/email-verifications/confirm",
            "/api/v1/auth/login",
            "/api/v1/auth/reissue",
            "/account-deletion",
            "/account-deletion/",
            "/account-deletion/index.html",
            "/privacy-policy",
            "/privacy-policy/",
            "/privacy-policy/index.html",
            "/child-safety",
            "/child-safety/",
            "/child-safety/index.html",
            "/api/v1/account-deletions",
            "/api/v1/account-deletions/email-verifications",
            "/api/v1/account-deletions/email-verifications/confirm",
            "/api/v1/legal-documents/signup",
            "/api/v1/categories",
            // 로그인 JWT 대신 AiSearchCapability의 서비스 키 + 단기 서명을 확인한다.
            "/internal/ai/search/categories",
            "/internal/ai/search/meetings",
            // 로그인 JWT 대신 AiModerationAuthenticator가 전용 서비스 키를 확인한다.
            "/internal/ai/moderation/policies/**",
            "/internal/ai/moderation/reports/**",
            "/ws",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final AccessTokenValidationRepository accessTokenValidationRepository;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(
                jwtTokenProvider,
                authenticationEntryPoint,
                accessTokenValidationRepository,
                PUBLIC_ENDPOINTS
        );

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
