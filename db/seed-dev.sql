-- 개발용 시드: 테스트 계정 1개 + Todo 100건
-- 페이지네이션·필터·정렬을 눈으로 확인하는 용도 (CLAUDE.md 4장 Phase 4).
-- 로그인 계정: seed-dev@example.com / pass1234
-- 재실행해도 안전하도록 계정은 ON CONFLICT DO NOTHING으로 멱등하게 만든다.

INSERT INTO users (email, password, nickname, provider, created_at, updated_at)
VALUES (
    'seed-dev@example.com',
    '$2a$10$cOh8rGw9oQVKM3JQk.lXy.oDtXnNxEgTA1uefYh7cv9JiuI3gmbiq', -- pass1234
    '시드테스터',
    'LOCAL',
    now(),
    now()
)
ON CONFLICT (email) DO NOTHING;

INSERT INTO todos (user_id, title, content, completed, priority, due_date, created_at, updated_at)
SELECT
    (SELECT id FROM users WHERE email = 'seed-dev@example.com'),
    '개발 시드 할 일 #' || n,
    '<p>시드 데이터 본문 ' || n || '</p>',
    (n % 3 = 0),
    (ARRAY['HIGH', 'MEDIUM', 'LOW'])[1 + (n % 3)],
    CASE WHEN n % 4 = 0 THEN NULL ELSE (CURRENT_DATE + ((n % 30) || ' days')::interval)::date END,
    now() - ((100 - n) || ' minutes')::interval,
    now() - ((100 - n) || ' minutes')::interval
FROM generate_series(1, 100) AS n;
