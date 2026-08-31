package com.example.todoapp.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * CustomOAuth2UserService가 던지는 유일한 실패 케이스(동일 이메일 로컬 계정 충돌)를
 * JSON 에러가 아니라 302 리다이렉트로 안내한다 (CLAUDE.md 6장).
 */
@Component
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        response.sendRedirect(frontendUrl + "/login?error=email_conflict");
    }
}
