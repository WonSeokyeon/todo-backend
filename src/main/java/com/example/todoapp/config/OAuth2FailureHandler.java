package com.example.todoapp.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * 구글 로그인 실패를 JSON 에러가 아니라 302 리다이렉트로 안내한다 (CLAUDE.md 6장).
 *
 * <p>동일 이메일 로컬 계정 충돌(email_conflict)만 전용 안내를 띄우고, 그 외의 실패
 * (토큰 교환 실패, state 불일치, userinfo 응답 오류 등)는 oauth_failed로 구분한다.
 * 예전에는 모든 실패를 email_conflict로 뭉뚱그려 보내서, 전혀 다른 원인의 실패도
 * "이미 이메일로 가입된 계정입니다"로 표시돼 원인 추적이 불가능했다(2026-09-10 실측 발견).
 */
@Slf4j
@Component
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    private static final String EMAIL_CONFLICT = "email_conflict";
    private static final String OAUTH_FAILED = "oauth_failed";

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        String errorCode = resolveErrorCode(exception);
        // 인증 실패는 4xx 성격이라 스택 트레이스 없이 코드·메시지만 남긴다 (CLAUDE.md 11장).
        log.warn("구글 로그인 실패 - errorCode={}, message={}", errorCode, exception.getMessage());
        response.sendRedirect(frontendUrl + "/login?error=" + errorCode);
    }

    /** 리다이렉트 URL에 실리므로 외부에서 들어온 문자열을 그대로 쓰지 않고 두 값 중 하나로만 좁힌다. */
    private String resolveErrorCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth2Exception
                && EMAIL_CONFLICT.equals(oauth2Exception.getError().getErrorCode())) {
            return EMAIL_CONFLICT;
        }
        return OAUTH_FAILED;
    }
}
