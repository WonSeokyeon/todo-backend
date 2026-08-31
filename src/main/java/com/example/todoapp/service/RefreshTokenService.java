package com.example.todoapp.service;

import com.example.todoapp.domain.RefreshToken;
import com.example.todoapp.domain.RefreshTokenRepository;
import com.example.todoapp.domain.User;
import com.example.todoapp.exception.BusinessException;
import com.example.todoapp.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 불투명 랜덤 Refresh Token 발급·회전·재사용 탐지 (CLAUDE.md 6장).
 * 평문 토큰은 어디에도 저장하지 않고 SHA-256 해시로만 대조한다.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32; // 256비트
    private static final long EXPIRATION_DAYS = 14;

    private final RefreshTokenRepository refreshTokenRepository;
    private final PlatformTransactionManager transactionManager;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String issue(User user) {
        String rawToken = generateRawToken();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        RefreshToken refreshToken = RefreshToken.issue(user, hash(rawToken), now.plusDays(EXPIRATION_DAYS));
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    /**
     * 사용된 Refresh Token을 폐기하고 새 토큰을 발급한다.
     * 이미 폐기됐거나 만료된 토큰이 다시 들어오면 탈취로 간주해 해당 사용자의 전체 토큰을 폐기한다.
     */
    @Transactional
    public RotatedToken rotate(String rawToken) {
        RefreshToken found = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (found.isRevoked() || found.isExpired(now)) {
            revokeAllInNewTransaction(found.getUser().getId());
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        found.revoke();
        User user = found.getUser();
        return new RotatedToken(user, issue(user));
    }

    @Transactional
    public void revokeByRawToken(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(RefreshToken::revoke);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllByUserId(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now(ZoneOffset.UTC));
    }

    /**
     * rotate() 안에서 revokeAllByUserId(...)를 그냥 호출하면 같은 인스턴스 내부 호출
     * (self-invocation)이라 Spring AOP 프록시를 거치지 않아 REQUIRES_NEW가 적용되지 않는다.
     * 그 결과 rotate()가 곧바로 던지는 BusinessException(RuntimeException) 때문에 호출자
     * 트랜잭션 전체가 롤백되면서, 방금 실행한 전체 폐기까지 함께 취소되어 탈취 대응이
     * 무효화된다(실측 확인). TransactionTemplate으로 프록시 우회 없이 별도 트랜잭션을
     * 직접 열어 즉시 커밋시킨다.
     */
    private void revokeAllInNewTransaction(Long userId) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status ->
                refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now(ZoneOffset.UTC)));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    public record RotatedToken(User user, String rawToken) {
    }
}
