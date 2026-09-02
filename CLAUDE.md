@../CLAUDE.md

# todo-backend 저장소 전용 보강

이 저장소를 단독으로 클론하면 위 임포트가 가리키는 부모 `CLAUDE.md`가 없다. 그 경우에도 아래는 이 저장소 안에서 바로 확인 가능한 사실이다.

- 빌드/실행/테스트: `./mvnw dependency:tree` · `./mvnw spring-boot:run` · `./mvnw test`
- 설정 파일 형식은 **`.properties`**다(`.yml` 아님). `application.properties` + 프로파일별 `application-{local,prod}.properties`, 테스트는 `src/test/resources/application-test.properties`(Phase 2에서 생성).
- 기본 활성 프로파일은 `local`(`spring.profiles.active=${SPRING_PROFILES_ACTIVE:local}`). 로컬 개발 시 `application-local.properties`에 `spring.datasource.password`를 채운다(이 파일은 `.gitignore` 대상, `.env.example` 참고).
- **`./mvnw test`는 `application-local.properties`를 상속받지 않는다(`local` 프로파일 전용).** `test` 프로파일은 `application.properties`의 `${DB_PASSWORD}`·`${JWT_SECRET}` placeholder를 그대로 쓰므로, 셸에 두 환경변수를 직접 설정해야 한다(값은 `application-local.properties`에 있는 것과 같아도 무방하다). 설정하지 않으면 `ApplicationContext failure threshold exceeded`로 근본 원인이 가려진 채 대량의 테스트가 한꺼번에 실패한다(Phase 10, 2026-09-02 실측 발견) — `DB_PASSWORD=... JWT_SECRET=... ./mvnw test`로 실행한다.
- 패키지는 `com.example.todoapp` 아래 기능별이 아니라 **계층별**로 나눈다: `domain / service / controller / dto / config / exception`.
- `.gitattributes`가 `/mvnw text eol=lf`를 강제한다. 삭제하거나 덮어쓰지 않는다.
