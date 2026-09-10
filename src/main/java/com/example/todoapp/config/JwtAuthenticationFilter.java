package com.example.todoapp.config;

import com.example.todoapp.domain.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer 헤더의 Access Token을 검증해 SecurityContext를 채운다.
 * 토큰이 없으면 그냥 통과시켜 permitAll 경로가 막히지 않게 한다.
 * 필터 단계의 예외는 GlobalExceptionHandler가 잡지 못하므로, 만료 여부를 요청 attribute에 남겨
 * JwtAuthenticationEntryPoint가 TOKEN_EXPIRED와 그 외 UNAUTHORIZED를 구분해 응답하게 한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String TOKEN_EXPIRED_ATTRIBUTE = "TOKEN_EXPIRED";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null) {
            try {
                Long userId = jwtTokenProvider.getUserId(token);
                userRepository.findByIdAndDeletedAtIsNull(userId).ifPresent(user -> {
                    var authentication = new UsernamePasswordAuthenticationToken(user, null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
                // 조회된 User가 없으면(탈퇴 등) 인증 미설정 상태로 두고 이후 401 UNAUTHORIZED로 이어진다.
            } catch (ExpiredJwtException e) {
                request.setAttribute(TOKEN_EXPIRED_ATTRIBUTE, Boolean.TRUE);
            } catch (JwtException | IllegalArgumentException e) {
                // 서명 오류·형식 오류 — 인증 미설정 상태로 두고 이후 401 UNAUTHORIZED로 이어진다.
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
