-- 스키마만 생성한다. 예제 데이터는 별도의 local/test 전용 스크립트에서 명시적으로 넣는다.
CREATE TABLE members (
    id BIGINT PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL
);
CREATE TABLE products (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    unit_price NUMERIC(19,2) NOT NULL CHECK (unit_price >= 0),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$')
);
-- 상품명/가격은 주문 시점 snapshot이다. 카탈로그 수정으로 이미 저장된 주문 금액이 바뀌지 않는다.
CREATE TABLE purchase_orders (
    id UUID PRIMARY KEY,
    owner_subject VARCHAR(255) NOT NULL CHECK (length(trim(owner_subject)) > 0),
    member_id BIGINT NOT NULL REFERENCES members(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    product_name VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 100),
    unit_price NUMERIC(19,2) NOT NULL CHECK (unit_price >= 0),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
-- FK는 부모 삭제/갱신 시 자식 존재 확인에도 사용되므로 해당 참조 열에 인덱스를 둔다.
CREATE INDEX idx_orders_member ON purchase_orders(member_id);
CREATE INDEX idx_orders_product ON purchase_orders(product_id);
