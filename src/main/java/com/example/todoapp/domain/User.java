package com.example.todoapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * users 테이블 매핑 엔티티. password는 소셜 전용 계정에서 null일 수 있다.
 * deletedAt은 이번 범위(회원 탈퇴 비목표)에서 항상 null이지만, 스키마와 조회 조건은 유지한다.
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_id")
    private String providerId;

    private LocalDateTime deletedAt;

    private User(String email, String password, String nickname, AuthProvider provider, String providerId) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.provider = provider;
        this.providerId = providerId;
    }

    public static User createLocal(String email, String password, String nickname) {
        return new User(email, password, nickname, AuthProvider.LOCAL, null);
    }

    public static User createGoogle(String email, String nickname, String providerId) {
        return new User(email, null, nickname, AuthProvider.GOOGLE, providerId);
    }
}
