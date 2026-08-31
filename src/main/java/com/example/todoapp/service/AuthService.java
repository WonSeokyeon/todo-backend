package com.example.todoapp.service;

import com.example.todoapp.config.JwtTokenProvider;
import com.example.todoapp.domain.User;
import com.example.todoapp.domain.UserRepository;
import com.example.todoapp.dto.LoginRequest;
import com.example.todoapp.dto.SignupRequest;
import com.example.todoapp.exception.BusinessException;
import com.example.todoapp.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    // 미가입 이메일과 비밀번호 오류를 구분하지 않는다 (계정 존재 여부 비노출 — PRD.md 5.1).
    private static final String LOGIN_FAILURE_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public AuthTokens signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        User user = User.createLocal(request.email(), passwordEncoder.encode(request.password()), request.nickname());
        userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthTokens login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, LOGIN_FAILURE_MESSAGE));

        // password가 null이면 소셜 전용 계정 — 이 경우도 동일한 실패 메시지로 응답한다.
        if (user.getPassword() == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, LOGIN_FAILURE_MESSAGE);
        }

        return issueTokens(user);
    }

    @Transactional
    public AuthTokens refresh(String rawRefreshToken) {
        RefreshTokenService.RotatedToken rotated = refreshTokenService.rotate(rawRefreshToken);
        String accessToken = jwtTokenProvider.generateAccessToken(rotated.user().getId(), rotated.user().getEmail());
        return new AuthTokens(accessToken, rotated.rawToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeByRawToken(rawRefreshToken);
    }

    private AuthTokens issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = refreshTokenService.issue(user);
        return new AuthTokens(accessToken, refreshToken);
    }

    public record AuthTokens(String accessToken, String refreshToken) {
    }
}
