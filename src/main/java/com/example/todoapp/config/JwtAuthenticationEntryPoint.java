package com.example.todoapp.config;

import com.example.todoapp.dto.ApiResponse;
import com.example.todoapp.exception.ErrorCode;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Security 필터 단계의 401도 ApiResponse 포맷으로 응답한다.
 * GlobalExceptionHandler는 필터에서 발생한 예외를 잡지 못하므로 별도로 둔다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        ErrorCode errorCode = Boolean.TRUE.equals(request.getAttribute(JwtAuthenticationFilter.TOKEN_EXPIRED_ATTRIBUTE))
                ? ErrorCode.TOKEN_EXPIRED
                : ErrorCode.UNAUTHORIZED;

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                objectMapper.writeValueAsString(ApiResponse.error(errorCode, errorCode.getDefaultMessage()))
        );
    }
}
