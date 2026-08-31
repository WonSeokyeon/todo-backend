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
	 * hibernate.jdbc.time_zone=UTC 설정만으로는 created_at/updated_at이 UTC로 저장되지 않는다
	 * (Repository 테스트로 9시간 어긋남을 실측 확인). Auditing이 값을 만드는 시점 자체를 UTC로 고정한다.
	 */
	@Bean
	public DateTimeProvider utcDateTimeProvider() {
		return () -> Optional.of(LocalDateTime.now(ZoneOffset.UTC));
	}

}
