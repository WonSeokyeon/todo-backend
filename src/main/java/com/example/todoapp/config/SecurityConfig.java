package com.example.todoapp.config;

import com.example.todoapp.service.CustomOAuth2UserService;
import jakarta.servlet.Filter;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Phase 1의 최소 permitAll(swagger·api-docs·error·actuator/health)을 그대로 유지하며
 * Phase 3에서 CSRF 비활성화·STATELESS 세션·CORS·JWT 필터·인증 예외 처리를 확장한다 (CLAUDE.md 6장).
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    // CloudFront가 커스텀 오리진 헤더로 지정한 X-Forwarded-Proto를 실제로는 오리진에
    // 전달하지 않아(2026-09-10 실측), CloudFront-Forwarded-Proto로 스킴을 보정하는
    // 필터를 Security 필터 체인보다도 앞(컨테이너 레벨)에 둔다.
    @Bean
    public FilterRegistrationBean<CloudFrontForwardedProtoFilter> cloudFrontForwardedProtoFilter() {
        FilterRegistrationBean<CloudFrontForwardedProtoFilter> registration =
                new FilterRegistrationBean<>(new CloudFrontForwardedProtoFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    // TEMP DEBUG (2026-09-10) — 위 보정 필터 적용 후 실제로 scheme이 바뀌었는지 확인한다.
    // 원인 확인 후 반드시 제거할 것.
    @Bean
    public FilterRegistrationBean<Filter> tempDebugFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>((request, response, chain) -> {
            if (request instanceof jakarta.servlet.http.HttpServletRequest httpRequest) {
                log.info("[TEMP-DEBUG-EARLY] uri={} scheme={} isSecure={} CloudFront-Forwarded-Proto={} X-Forwarded-Proto={} Host={}",
                        httpRequest.getRequestURI(), httpRequest.getScheme(), httpRequest.isSecure(),
                        httpRequest.getHeader("CloudFront-Forwarded-Proto"), httpRequest.getHeader("X-Forwarded-Proto"),
                        httpRequest.getHeader("Host"));
            }
            chain.doFilter(request, response);
        });
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // JWT stateless — CSRF 토큰을 발급하는 경로가 없다.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        // /actuator/health 만 노출한다. 그 외 /actuator/** 는 인증 필요(부모 CLAUDE.md 절대규칙 9).
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/error").permitAll()
                        .requestMatchers("/api/v1/auth/signup", "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        // 첨부 업로드·조회는 Authorization 헤더가 아니라 쿼리의 서명 토큰으로 인증한다.
                        // S3 presigned URL은 헤더가 붙으면 서명이 깨지고, img 태그는 헤더를 실을 수 없다.
                        // 컨트롤러가 토큰을 직접 검증한다 — /auth/refresh와 같은 패턴이다 (CLAUDE.md 5장).
                        .requestMatchers("/api/v1/attachments/*/upload", "/api/v1/attachments/*/raw").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler(oAuth2FailureHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Refresh Token을 httpOnly 쿠키로 주고받으므로 자격 증명 포함이 필수다.
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
