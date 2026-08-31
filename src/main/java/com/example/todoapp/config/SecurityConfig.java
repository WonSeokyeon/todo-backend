package com.example.todoapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Phase 1 임시 최소 설정. Swagger UI 접속 확인(Phase 1 DoD)을 위해
 * /swagger-ui/**, /v3/api-docs/**, /error 만 permitAll 한다.
 * CSRF 비활성화, STATELESS 세션, JWT 인증 필터, CORS 등 전체 보안 설정은
 * Phase 3(CLAUDE.md 6장 SecurityConfig 인가 경로)에서 이 빈을 확장해 완성한다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                // /actuator/health 만 노출한다. 그 외 /actuator/** 는 인증 필요(부모 CLAUDE.md 절대규칙 9).
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/error").permitAll()
                .anyRequest().authenticated()
        );
        return http.build();
    }
}
