package com.example.todoapp.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 저장하면_createdAt이_UTC_기준으로_자동_기록된다() {
        User saved = userRepository.saveAndFlush(User.createLocal("test@example.com", "encodedPassword", "테스터"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        // saved.getCreatedAt()은 JPA Auditing이 메모리 상 엔티티에 채운 값일 뿐이라
        // "Java가 UTC를 올바르게 계산했는가"만 검증하고, "그 값이 DB에 실제로 어떻게
        // 저장됐는가"(Hibernate/JDBC 바인딩 단계의 왜곡)는 검증하지 못한다 — 2026-09-02
        // 실측에서 이 맹점 때문에 9시간 저장 왜곡 회귀가 테스트 통과 상태로 남아있었다.
        // JDBC로 원본 컬럼 값을 직접 재조회해 실제 저장값을 검증한다.
        Instant storedCreatedAtAsUtc = jdbcTemplate
                .queryForObject("SELECT created_at FROM users WHERE id = ?", LocalDateTime.class, saved.getId())
                .atZone(ZoneOffset.UTC)
                .toInstant();
        assertThat(Duration.between(storedCreatedAtAsUtc, Instant.now()).abs()).isLessThan(Duration.ofMinutes(1));
    }

    @Test
    void findByEmailAndDeletedAtIsNull로_조회된다() {
        userRepository.saveAndFlush(User.createLocal("find@example.com", "pw", "닉네임"));

        assertThat(userRepository.findByEmailAndDeletedAtIsNull("find@example.com")).isPresent();
        assertThat(userRepository.findByEmailAndDeletedAtIsNull("nope@example.com")).isEmpty();
    }
}
