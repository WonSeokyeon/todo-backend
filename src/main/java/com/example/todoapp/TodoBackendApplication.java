package com.example.todoapp;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// @DataJpaTest가 별도 @Configuration 클래스는 로드하지 않으므로
// @EnableJpaAuditing과 UTC DateTimeProvider 빈 모두 반드시 이 메인 클래스에 둔다.
@EnableJpaAuditing(dateTimeProviderRef = "utcDateTimeProvider")
@SpringBootApplication
public class TodoBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(TodoBackendApplication.class, args);
	}

	/**
	 * LocalDateTime.now()는 JVM 기본 타임존(KST)의 벽시계 값을 반환하므로
	 * Auditing이 값을 만드는 시점 자체를 UTC로 고정한다.
	 *
	 * application.properties에 hibernate.jdbc.time_zone=UTC를 함께 설정하면 안 된다 — JVM
	 * 기본 타임존이 KST인 환경에서는 Hibernate가 이미 UTC인 이 값을 "KST 로컬 시각"으로
	 * 다시 해석해 UTC로 한 번 더 변환해버려, 실제 저장값이 9시간 뒤로 밀린다. saveAndFlush
	 * 직후의 메모리 상 엔티티 값만 비교하는 테스트는 이 왜곡을 잡지 못하므로, UTC 저장은
	 * JDBC로 DB에 실제로 쓰인 값을 재조회해 검증해야 한다(2026-09-02 실측 발견).
	 */
	@Bean
	public DateTimeProvider utcDateTimeProvider() {
		return () -> Optional.of(LocalDateTime.now(ZoneOffset.UTC));
	}

}
