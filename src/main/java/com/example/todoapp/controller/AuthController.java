package com.example.todoapp.controller;

import com.example.todoapp.domain.User;
import com.example.todoapp.dto.ApiResponse;
import com.example.todoapp.dto.LoginRequest;
import com.example.todoapp.dto.SignupRequest;
import com.example.todoapp.dto.TokenResponse;
import com.example.todoapp.dto.UserResponse;
import com.example.todoapp.exception.BusinessException;
import com.example.todoapp.exception.ErrorCode;
import com.example.todoapp.service.AuthService;
import com.example.todoapp.service.AuthService.AuthTokens;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final Duration REFRESH_TOKEN_MAX_AGE = Duration.ofDays(14);

    private final AuthService authService;

    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    @Value("${app.cookie.same-site}")
    private String cookieSameSite;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<TokenResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return withRefreshCookie(authService.signup(request));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        return withRefreshCookie(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal User user,
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        ResponseCookie expiredCookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .body(ApiResponse.success(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(user)));
    }

    private ResponseEntity<ApiResponse<TokenResponse>> withRefreshCookie(AuthTokens tokens) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, tokens.refreshToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/v1/auth")
                .maxAge(REFRESH_TOKEN_MAX_AGE)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(new TokenResponse(tokens.accessToken())));
    }
}
