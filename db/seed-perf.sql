-- 성능 DoD 측정 전용 시드: seed-dev.sql과 같은 계정에 Todo 10,000건.
-- CLAUDE.md 4장이 지목한 유일한 성능 위험(LOWER(title) LIKE '%키워드%'의 인덱스 미사용)을
-- 실측하려면 데이터가 충분해야 한다. 제목 절반에 '회의' 키워드를 섞어 검색 경로를 재현한다.
-- 사용법: seed-dev.sql로 계정을 먼저 만든 뒤(또는 이 스크립트가 자체적으로도 만든다) 적용한다.
-- 개발용 100건과 별개로 추가되므로, 반복 실행하면 그만큼 더 쌓인다(성능 측정 전용이라 허용).

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
    CASE WHEN n % 2 = 0 THEN '회의 준비 #' || n ELSE '개인 업무 #' || n END,
    '<p>성능 측정용 본문 ' || n || '</p>',
    (n % 3 = 0),
    (ARRAY['HIGH', 'MEDIUM', 'LOW'])[1 + (n % 3)],
    CASE WHEN n % 4 = 0 THEN NULL ELSE (CURRENT_DATE + ((n % 60) || ' days')::interval)::date END,
    now() - ((10000 - n) || ' seconds')::interval,
    now() - ((10000 - n) || ' seconds')::interval
FROM generate_series(1, 10000) AS n;
