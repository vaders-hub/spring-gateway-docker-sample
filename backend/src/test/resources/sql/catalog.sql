-- 12단계 로컬 학습자가 명시적으로 실행하는 데이터. Flyway 경로 밖이며 JAR에 포함하지 않는다.
-- 재실행해도 기존 수정값/주문을 덮어쓰지 않는다. 계정·비밀번호 데이터가 아닌 업무 예제다.
BEGIN;
INSERT INTO members(id, display_name) VALUES (1, 'Sample Member'), (2, 'Second Member')
ON CONFLICT (id) DO NOTHING;
INSERT INTO products(id, name, unit_price, currency)
VALUES (1, 'Keyboard', 50000, 'KRW'), (2, 'Mouse', 20000, 'KRW')
ON CONFLICT (id) DO NOTHING;
COMMIT;
