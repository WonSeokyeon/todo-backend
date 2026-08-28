# todo-backend

Todo List 서비스의 백엔드 API 서버입니다. Spring Boot 4.1.1 / JDK 21 / PostgreSQL 로 구성되어 있습니다.

---

## 시작하기

### 1. 저장소 클론 후 git 훅 활성화 (필수)

이 저장소는 커밋 전 안전 검사를 git 훅으로 수행합니다.
**클론한 뒤 반드시 한 번 실행해 주세요.** 실행하지 않으면 검사가 동작하지 않습니다.

```bash
# Windows (명령 프롬프트 / PowerShell)
.githooks\install.cmd

# macOS / Linux / Git Bash
sh .githooks/install.sh
```

내부적으로는 `git config core.hooksPath .githooks` 한 줄을 설정합니다.
Node.js 나 husky 같은 추가 의존성은 필요하지 않습니다.

### 2. 로컬 설정 파일 준비

DB 비밀번호 등 민감한 값은 저장소에 커밋하지 않습니다. (CLAUDE.md 절대규칙 9)

```bash
cp src/main/resources/application-local.properties.example src/main/resources/application-local.properties
```

복사한 `application-local.properties` 에 로컬 PostgreSQL 비밀번호를 채웁니다.
이 파일은 `.gitignore` 에 등록되어 있어 커밋되지 않습니다.

### 3. 실행

```bash
./mvnw spring-boot:run     # 개발 서버 실행 (기본 프로파일: local)
./mvnw test                # 테스트
./mvnw test-compile        # 컴파일 검증
```

---

## 설정 구조

| 파일 | 용도 | 커밋 여부 |
|---|---|---|
| `application.properties` | 공통 설정. 민감한 값은 `${ENV_VAR}` 로 참조만 한다 | ✅ 커밋 |
| `application-local.properties` | 개인 로컬 값 (DB 비밀번호 등) | ❌ 제외 |
| `application-local.properties.example` | 위 파일의 템플릿 | ✅ 커밋 |

배포 환경에서는 `SPRING_PROFILES_ACTIVE` 와 `DB_PASSWORD` 등의 환경변수로 값을 주입합니다.

---

## git 훅

`.githooks/` 에 순수 셸 스크립트로 작성되어 있습니다.

### `pre-commit` — 커밋 직전 (빠름)

| 검사 | 내용 |
|---|---|
| 비밀 파일 차단 | `.env`, `application-local.*`, `*.pem`, `*.key`, `*.jks` 등이 커밋 대상에 있으면 중단 |
| 하드코딩 비밀 탐지 | 추가된 줄에서 JWT 토큰, AWS 액세스 키, 리터럴 비밀번호를 찾아 중단 |
| 빌드 산출물 차단 | `target/`, `*.class` 가 섞여 들어가면 중단 |
| 큰 파일 경고 | 1MB 초과 파일은 경고만 표시 |

`${DB_PASSWORD}` 같은 환경변수 참조와 주석 줄은 정상으로 판단해 통과시킵니다.

### `commit-msg` — 커밋 메시지 검사

Conventional Commits 형식을 강제합니다. (CLAUDE.md 6장)

```
<타입>(<범위>): <제목>

타입: feat fix docs style refactor perf test build ci chore revert
제목: 72자 이내, 끝에 마침표 없음
```

```bash
# 올바른 예
git commit -m "feat: 할 일 생성 API 추가"
git commit -m "fix(auth): 리프레시 토큰 회전 시 이전 토큰이 폐기되지 않던 문제 수정"

# 거부되는 예
git commit -m "할일 API 추가"        # 타입 없음
git commit -m "feat: API 추가."       # 끝에 마침표
```

### `pre-push` — 푸시 직전

`./mvnw test-compile` 로 컴파일을 검증합니다.
커밋마다 실행하면 느리므로 상대적으로 드문 push 시점에 한 번만 확인합니다.

### 검사를 건너뛰어야 할 때

오탐이거나 급한 경우에만 사용합니다.

```bash
git commit --no-verify
git push --no-verify
```

---

## 코드 스타일

`.editorconfig` 로 들여쓰기(Java 4칸)와 인코딩(UTF-8), 줄바꿈(LF)을 고정합니다.
`.gitattributes` 로 저장소의 줄바꿈을 LF 로 정규화하므로, Windows 의 `core.autocrlf` 설정과 무관하게 동일한 결과가 나옵니다.

주석은 한글로 작성하고, 코드 식별자(클래스/변수/메서드명)는 영문으로 작성합니다. (CLAUDE.md 절대규칙 10)
