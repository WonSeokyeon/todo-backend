package com.example.todoapp.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void 저장하면_createdAt이_UTC_기준으로_자동_기록된다() {
        User saved = userRepository.saveAndFlush(User.createLocal("test@example.com", "encodedPassword", "테스터"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        // hibernate.jdbc.time_zone=UTC가 없으면 KST(+09:00) 기준으로 저장돼 9시간 어긋난다.
        Instant createdAtAsUtc = saved.getCreatedAt().atZone(ZoneOffset.UTC).toInstant();
        assertThat(Duration.between(createdAtAsUtc, Instant.now()).abs()).isLessThan(Duration.ofMinutes(1));
    }

    @Test
    void findByEmailAndDeletedAtIsNull로_조회된다() {
        userRepository.saveAndFlush(User.createLocal("find@example.com", "pw", "닉네임"));

        assertThat(userRepository.findByEmailAndDeletedAtIsNull("find@example.com")).isPresent();
        assertThat(userRepository.findByEmailAndDeletedAtIsNull("nope@example.com")).isEmpty();
    }
}
