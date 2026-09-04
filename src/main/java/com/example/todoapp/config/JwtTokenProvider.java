package com.example.todoapp.config;

import com.example.todoapp.exception.BusinessException;
import com.example.todoapp.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Access Token(JWT)과 첨부 전용 단기 서명 토큰의 생성·검증.
 * jjwt 0.12 신문법(verifyWith/signWith)만 사용한다.
 * Access Token의 sub는 이메일이 아니라 user id를 담는다 (CLAUDE.md 6장).
 */
@Component
public class JwtTokenProvider {

    private static final Duration ACCESS_TOKEN_EXPIRATION = Duration.ofMinutes(30);

    /** 첨부 토큰의 용도를 담는 클레임. Access Token에는 이 클레임이 없다. */
    private static final String PURPOSE_CLAIM = "typ";

    private final SecretKey key;
    private final Duration uploadTokenExpiration;
    private final Duration viewTokenExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${app.storage.upload-url-expiry-minutes:10}") long uploadExpiryMinutes,
            @Value("${app.storage.view-url-expiry-minutes:30}") long viewExpiryMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.uploadTokenExpiration = Duration.ofMinutes(uploadExpiryMinutes);
        this.viewTokenExpiration = Duration.ofMinutes(viewExpiryMinutes);
    }

    public String generateAccessToken(Long userId, String email) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + ACCESS_TOKEN_EXPIRATION.toMillis());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseAccessClaims(token).getSubject());
    }

    public boolean validate(String token) {
        try {
            parseAccessClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 첨부 전용 단기 토큰을 발급한다. sub는 attachmentId, typ는 용도다.
     *
     * <p>업로드·조회 URL을 Authorization 헤더가 아니라 쿼리 파라미터로 인증하는 이유는
     * S3 presigned URL과 호출 방식을 맞추기 위해서다. 헤더를 붙이면 S3는 서명이 깨지고,
     * img 태그는 애초에 헤더를 실을 수 없다 (CLAUDE.md 5장).
     */
    public String generateAttachmentToken(Long attachmentId, AttachmentTokenPurpose purpose) {
        Duration expiration = purpose == AttachmentTokenPurpose.UPLOAD
                ? uploadTokenExpiration
                : viewTokenExpiration;
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(attachmentId))
                .claim(PURPOSE_CLAIM, purpose.getClaimValue())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration.toMillis()))
                .signWith(key)
                .compact();
    }

    /**
     * 첨부 토큰을 검증하고 attachmentId를 돌려준다.
     * 서명·만료뿐 아니라 typ가 기대한 용도와 정확히 일치하는지도 확인한다.
     *
     * @throws BusinessException 서명·만료·용도 중 하나라도 어긋나면 401
     */
    public Long parseAttachmentToken(String token, AttachmentTokenPurpose expected) {
        try {
            Claims claims = parseClaims(token);
            String purpose = claims.get(PURPOSE_CLAIM, String.class);
            if (!expected.getClaimValue().equals(purpose)) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    /**
     * Access Token 전용 파싱. typ 클레임이 있으면 첨부 토큰이므로 거부한다.
     *
     * <p>두 토큰이 같은 키로 서명되므로 이 검사가 없으면 첨부 토큰을 Bearer로 보낼 수 있다.
     * 그 경우 sub(attachmentId)가 userId로 해석되어 남의 계정으로 인증되는 결과가 된다.
     *
     * <p>BusinessException이 아니라 JwtException을 던진다 — JwtAuthenticationFilter가
     * JwtException 계열만 잡아 "인증 미설정"으로 넘기기 때문이다. 다른 예외를 던지면
     * 필터 체인을 뚫고 나가 500이 된다.
     */
    private Claims parseAccessClaims(String token) {
        Claims claims = parseClaims(token);
        if (claims.get(PURPOSE_CLAIM) != null) {
            throw new JwtException("첨부 전용 토큰은 Access Token으로 사용할 수 없습니다.");
        }
        return claims;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
