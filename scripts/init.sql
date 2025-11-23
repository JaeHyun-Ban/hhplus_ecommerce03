-- ========================================
-- E-Commerce Database Schema
-- MySQL 8.0
-- Created: 2025-11-20
-- Version: 3.0
-- ========================================

-- 데이터베이스 사용
USE ecommerce;

-- 기존 테이블 삭제 (주의: 개발 환경에서만 사용)
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS outbound_events;
DROP TABLE IF EXISTS restock_notifications;
DROP TABLE IF EXISTS product_statistics;
DROP TABLE IF EXISTS stock_history;
DROP TABLE IF EXISTS balance_history;
DROP TABLE IF EXISTS order_coupons;
DROP TABLE IF EXISTS user_coupons;
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS order_sequences;
DROP TABLE IF EXISTS cart_items;
DROP TABLE IF EXISTS carts;
DROP TABLE IF EXISTS coupons;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

-- ========================================
-- 1. 사용자 테이블 (users)
-- ========================================
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL UNIQUE COMMENT '이메일 (로그인 ID)',
    password VARCHAR(255) NOT NULL COMMENT '비밀번호 (암호화)',
    name VARCHAR(50) NOT NULL COMMENT '사용자 이름',
    balance DECIMAL(15,2) NOT NULL DEFAULT 0.00 COMMENT '잔액',
    role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '역할 (USER, ADMIN)',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '상태 (ACTIVE, INACTIVE, SUSPENDED)',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    INDEX idx_email (email),
    INDEX idx_status (status),
    CONSTRAINT chk_balance CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='사용자';

-- ========================================
-- 2. 카테고리 테이블 (categories)
-- ========================================
CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE COMMENT '카테고리명',
    description VARCHAR(500) COMMENT '설명',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='상품 카테고리';

-- ========================================
-- 3. 상품 테이블 (products)
-- ========================================
CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL COMMENT '상품명',
    description VARCHAR(1000) COMMENT '상품 설명',
    price DECIMAL(15,2) NOT NULL COMMENT '가격',
    stock INT NOT NULL DEFAULT 0 COMMENT '재고 수량',
    safety_stock INT NOT NULL DEFAULT 10 COMMENT '안전 재고',
    category_id BIGINT COMMENT '카테고리 ID',
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' COMMENT '상태 (AVAILABLE, OUT_OF_STOCK, DISCONTINUED)',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 락 버전',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_name (name),
    INDEX idx_category_id (category_id),
    INDEX idx_status_stock (status, stock),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT chk_price CHECK (price >= 0),
    CONSTRAINT chk_stock CHECK (stock >= 0),
    CONSTRAINT chk_safety_stock CHECK (safety_stock >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='상품';

-- ========================================
-- 4. 장바구니 테이블 (carts)
-- ========================================
CREATE TABLE carts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE COMMENT '사용자 ID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='장바구니';

-- ========================================
-- 5. 장바구니 항목 테이블 (cart_items)
-- ========================================
CREATE TABLE cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cart_id BIGINT NOT NULL COMMENT '장바구니 ID',
    product_id BIGINT NOT NULL COMMENT '상품 ID',
    quantity INT NOT NULL DEFAULT 1 COMMENT '수량',
    price_at_add DECIMAL(15,2) NOT NULL COMMENT '담을 당시 가격',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cart_id (cart_id),
    INDEX idx_product_id (product_id),
    UNIQUE KEY uk_cart_product (cart_id, product_id) COMMENT '장바구니당 상품 중복 방지',
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT chk_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='장바구니 항목';

-- ========================================
-- 6. 쿠폰 테이블 (coupons)
-- ========================================
CREATE TABLE coupons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE COMMENT '쿠폰 코드',
    name VARCHAR(100) NOT NULL COMMENT '쿠폰명',
    description VARCHAR(500) COMMENT '설명',
    type VARCHAR(20) NOT NULL COMMENT '할인 유형 (PERCENTAGE, FIXED_AMOUNT)',
    discount_value DECIMAL(15,2) NOT NULL COMMENT '할인값 (퍼센트 또는 고정금액)',
    minimum_order_amount DECIMAL(15,2) COMMENT '최소 주문 금액',
    maximum_discount_amount DECIMAL(15,2) COMMENT '최대 할인 금액',
    applicable_category_id BIGINT COMMENT '적용 가능 카테고리',
    total_quantity INT NOT NULL COMMENT '총 발급 수량',
    issued_quantity INT NOT NULL DEFAULT 0 COMMENT '발급된 수량',
    max_issue_per_user INT NOT NULL DEFAULT 1 COMMENT '사용자당 최대 발급 수',
    issue_start_at TIMESTAMP NOT NULL COMMENT '발급 시작 일시',
    issue_end_at TIMESTAMP NOT NULL COMMENT '발급 종료 일시',
    valid_from TIMESTAMP NOT NULL COMMENT '사용 가능 시작 일시',
    valid_until TIMESTAMP NOT NULL COMMENT '사용 가능 종료 일시',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '상태 (ACTIVE, INACTIVE, EXHAUSTED)',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 락 버전 (선착순 제어)',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_code (code),
    INDEX idx_status_type (status, type),
    INDEX idx_issue_period (issue_start_at, issue_end_at),
    INDEX idx_valid_period (valid_from, valid_until),
    CONSTRAINT fk_coupons_category FOREIGN KEY (applicable_category_id) REFERENCES categories(id),
    CONSTRAINT chk_issued_quantity CHECK (issued_quantity <= total_quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='쿠폰 마스터';

-- ========================================
-- 7. 사용자 쿠폰 테이블 (user_coupons)
-- ========================================
CREATE TABLE user_coupons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    coupon_id BIGINT NOT NULL COMMENT '쿠폰 ID',
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED' COMMENT '상태 (ISSUED, USED, EXPIRED)',
    issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '발급 일시',
    used_at TIMESTAMP NULL COMMENT '사용 일시',
    expired_at TIMESTAMP NULL COMMENT '만료 일시',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 락 버전',
    INDEX idx_user_id (user_id),
    INDEX idx_coupon_id (coupon_id),
    INDEX idx_status (status),
    UNIQUE KEY uk_user_coupon (user_id, coupon_id) COMMENT '사용자당 쿠폰 중복 발급 방지',
    CONSTRAINT fk_user_coupons_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_coupons_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='사용자 보유 쿠폰';

-- ========================================
-- 8. 주문 시퀀스 테이블 (order_sequences)
-- ========================================
CREATE TABLE order_sequences (
    order_date VARCHAR(10) NOT NULL PRIMARY KEY COMMENT '주문 날짜 (yyyy-MM-dd)',
    sequence BIGINT NOT NULL DEFAULT 0 COMMENT '해당 날짜의 주문 시퀀스'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='주문 번호 시퀀스 관리';

-- ========================================
-- 9. 주문 테이블 (orders)
-- ========================================
CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(50) NOT NULL UNIQUE COMMENT '주문 번호 (ORD-YYYYMMDD-NNNNNN)',
    user_id BIGINT NOT NULL COMMENT '주문자 ID',
    total_amount DECIMAL(15,2) NOT NULL COMMENT '총 주문 금액',
    discount_amount DECIMAL(15,2) NOT NULL DEFAULT 0.00 COMMENT '할인 금액',
    final_amount DECIMAL(15,2) NOT NULL COMMENT '최종 결제 금액',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '주문 상태 (PENDING, PAID, CANCELLED)',
    ordered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '주문 일시',
    paid_at TIMESTAMP NULL COMMENT '결제 완료 일시',
    cancelled_at TIMESTAMP NULL COMMENT '취소 일시',
    cancellation_reason VARCHAR(500) COMMENT '취소 사유',
    idempotency_key VARCHAR(100) NOT NULL UNIQUE COMMENT '멱등성 키 (중복 주문 방지)',
    INDEX idx_user_id (user_id),
    INDEX idx_order_number (order_number),
    INDEX idx_status (status),
    INDEX idx_idempotency_key (idempotency_key),
    INDEX idx_ordered_at (ordered_at),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_total_amount CHECK (total_amount >= 0),
    CONSTRAINT chk_discount_amount CHECK (discount_amount >= 0),
    CONSTRAINT chk_final_amount CHECK (final_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='주문';

-- ========================================
-- 10. 주문 항목 테이블 (order_items)
-- ========================================
CREATE TABLE order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL COMMENT '주문 ID',
    product_id BIGINT NOT NULL COMMENT '상품 ID',
    product_name VARCHAR(200) NOT NULL COMMENT '주문 당시 상품명',
    quantity INT NOT NULL COMMENT '주문 수량',
    price_at_order DECIMAL(15,2) NOT NULL COMMENT '주문 당시 가격',
    subtotal DECIMAL(15,2) NOT NULL COMMENT '소계 (가격 × 수량)',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT chk_order_quantity CHECK (quantity > 0),
    CONSTRAINT chk_subtotal CHECK (subtotal >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='주문 항목';

-- ========================================
-- 11. 주문 쿠폰 테이블 (order_coupons)
-- ========================================
CREATE TABLE order_coupons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL COMMENT '주문 ID',
    user_coupon_id BIGINT NOT NULL COMMENT '사용자 쿠폰 ID',
    coupon_name VARCHAR(100) NOT NULL COMMENT '쿠폰명',
    discount_amount DECIMAL(15,2) NOT NULL COMMENT '할인 금액',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_id (order_id),
    INDEX idx_user_coupon_id (user_coupon_id),
    CONSTRAINT fk_order_coupons_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_coupons_user_coupon FOREIGN KEY (user_coupon_id) REFERENCES user_coupons(id),
    CONSTRAINT chk_discount CHECK (discount_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='주문 사용 쿠폰';

-- ========================================
-- 12. 결제 테이블 (payments)
-- ========================================
CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE COMMENT '주문 ID',
    amount DECIMAL(15,2) NOT NULL COMMENT '결제 금액',
    method VARCHAR(20) NOT NULL COMMENT '결제 수단 (BALANCE, CARD, BANK_TRANSFER)',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '결제 상태 (PENDING, COMPLETED, FAILED, CANCELLED)',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    completed_at TIMESTAMP NULL COMMENT '완료 일시',
    failure_reason VARCHAR(500) COMMENT '실패 사유',
    INDEX idx_order_id (order_id),
    INDEX idx_status_method (status, method),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT chk_payment_amount CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='결제';

-- ========================================
-- 13. 잔액 이력 테이블 (balance_history)
-- ========================================
CREATE TABLE balance_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    transaction_type VARCHAR(20) NOT NULL COMMENT '거래 유형 (CHARGE, USE, REFUND)',
    amount DECIMAL(15,2) NOT NULL COMMENT '거래 금액',
    balance_before DECIMAL(15,2) NOT NULL COMMENT '거래 전 잔액',
    balance_after DECIMAL(15,2) NOT NULL COMMENT '거래 후 잔액',
    description VARCHAR(500) COMMENT '설명',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_transaction_type (transaction_type),
    INDEX idx_created_at (created_at),
    CONSTRAINT fk_balance_history_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='잔액 거래 이력';

-- ========================================
-- 14. 재고 이력 테이블 (stock_history)
-- ========================================
CREATE TABLE stock_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL COMMENT '상품 ID',
    transaction_type VARCHAR(20) NOT NULL COMMENT '거래 유형 (INCREASE, DECREASE, ADJUSTMENT)',
    quantity INT NOT NULL COMMENT '변동 수량',
    stock_before INT NOT NULL COMMENT '거래 전 재고',
    stock_after INT NOT NULL COMMENT '거래 후 재고',
    description VARCHAR(500) COMMENT '설명',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_product_id (product_id),
    INDEX idx_transaction_type (transaction_type),
    INDEX idx_created_at (created_at),
    CONSTRAINT fk_stock_history_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='재고 변동 이력';

-- ========================================
-- 15. 상품 통계 테이블 (product_statistics)
-- ========================================
CREATE TABLE product_statistics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL COMMENT '상품 ID',
    date DATE NOT NULL COMMENT '통계 날짜',
    sales_count INT NOT NULL DEFAULT 0 COMMENT '판매 수량',
    sales_amount DECIMAL(15,2) NOT NULL DEFAULT 0.00 COMMENT '판매 금액',
    view_count INT NOT NULL DEFAULT 0 COMMENT '조회 수',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product_date (product_id, date) COMMENT '상품별 일자별 유일성',
    INDEX idx_date (date),
    INDEX idx_sales_count (sales_count),
    CONSTRAINT fk_product_statistics_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='상품 통계';

-- ========================================
-- 16. 재입고 알림 테이블 (restock_notifications)
-- ========================================
CREATE TABLE restock_notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    product_id BIGINT NOT NULL COMMENT '상품 ID',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '상태 (PENDING, NOTIFIED, CANCELLED)',
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '요청 일시',
    notified_at TIMESTAMP NULL COMMENT '알림 발송 일시',
    INDEX idx_user_id (user_id),
    INDEX idx_product_id (product_id),
    INDEX idx_status (status),
    UNIQUE KEY uk_user_product (user_id, product_id) COMMENT '사용자당 상품별 중복 신청 방지',
    CONSTRAINT fk_restock_notifications_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_restock_notifications_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='재입고 알림';

-- ========================================
-- 17. 외부 이벤트 테이블 (outbound_events)
-- ========================================
CREATE TABLE outbound_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL COMMENT '이벤트 유형 (ORDER_CREATED, ORDER_CANCELLED)',
    payload JSON NOT NULL COMMENT '이벤트 데이터 (JSON)',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '상태 (PENDING, SENT, FAILED)',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '재시도 횟수',
    error_message VARCHAR(1000) COMMENT '에러 메시지',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    sent_at TIMESTAMP NULL COMMENT '전송 성공 일시',
    failed_at TIMESTAMP NULL COMMENT '전송 실패 일시',
    INDEX idx_event_type (event_type),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='외부 시스템 연동 이벤트';

-- ========================================
-- 초기 데이터 삽입
-- ========================================

-- 카테고리 초기 데이터
INSERT INTO categories (name, description) VALUES
('전자기기', '스마트폰, 노트북, 태블릿 등'),
('의류', '남성/여성 의류'),
('도서', '도서 및 전자책'),
('생활용품', '생활 필수품'),
('식품', '식품 및 음료');

-- 테스트용 사용자 (비밀번호: password123)
INSERT INTO users (email, password, name, balance, role, status) VALUES
('admin@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '관리자', 10000000.00, 'ADMIN', 'ACTIVE'),
('user1@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '홍길동', 500000.00, 'USER', 'ACTIVE'),
('user2@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '김철수', 300000.00, 'USER', 'ACTIVE'),
('user3@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '이영희', 200000.00, 'USER', 'ACTIVE');

-- 테스트용 상품
INSERT INTO products (name, description, price, stock, safety_stock, category_id, status) VALUES
('iPhone 15 Pro', '최신 아이폰', 1500000.00, 100, 10, 1, 'AVAILABLE'),
('Galaxy S24', '삼성 최신 스마트폰', 1300000.00, 150, 15, 1, 'AVAILABLE'),
('MacBook Pro 14', '애플 노트북', 2500000.00, 50, 5, 1, 'AVAILABLE'),
('AirPods Pro', '무선 이어폰', 350000.00, 200, 20, 1, 'AVAILABLE'),
('나이키 운동화', '편한 운동화', 150000.00, 80, 10, 2, 'AVAILABLE'),
('자바의 정석', '자바 프로그래밍 책', 35000.00, 50, 5, 3, 'AVAILABLE'),
('무선 청소기', '다이슨 청소기', 800000.00, 30, 3, 4, 'AVAILABLE'),
('유기농 쌀 10kg', '프리미엄 쌀', 45000.00, 100, 10, 5, 'AVAILABLE');

-- 테스트용 쿠폰
INSERT INTO coupons (code, name, description, type, discount_value, minimum_order_amount, maximum_discount_amount,
                     total_quantity, issued_quantity, max_issue_per_user,
                     issue_start_at, issue_end_at, valid_from, valid_until, status) VALUES
('WELCOME2025', '신규 가입 쿠폰', '신규 가입 고객 10% 할인', 'PERCENTAGE', 10.00, 10000.00, 50000.00,
 1000, 0, 1,
 '2025-01-01 00:00:00', '2025-12-31 23:59:59', '2025-01-01 00:00:00', '2025-12-31 23:59:59', 'ACTIVE'),
('SPRING50K', '봄맞이 할인', '5만원 할인 쿠폰', 'FIXED_AMOUNT', 50000.00, 500000.00, 50000.00,
 500, 0, 1,
 '2025-03-01 00:00:00', '2025-05-31 23:59:59', '2025-03-01 00:00:00', '2025-05-31 23:59:59', 'ACTIVE'),
('ELECTRON20', '전자기기 20% 할인', '전자기기 카테고리 20% 할인', 'PERCENTAGE', 20.00, 100000.00, 100000.00,
 200, 0, 2,
 '2025-01-01 00:00:00', '2025-12-31 23:59:59', '2025-01-01 00:00:00', '2025-12-31 23:59:59', 'ACTIVE');

-- 오늘 날짜의 주문 시퀀스 초기화
INSERT INTO order_sequences (order_date, sequence) VALUES
(DATE_FORMAT(NOW(), '%Y-%m-%d'), 0);

-- ========================================
-- 완료 메시지
-- ========================================
SELECT '========================================' AS '';
SELECT 'Database schema creation completed!' AS 'Status';
SELECT '========================================' AS '';
SELECT 'Tables created:' AS '';
SELECT '  - users (4 test users)' AS '';
SELECT '  - categories (5 categories)' AS '';
SELECT '  - products (8 test products)' AS '';
SELECT '  - coupons (3 test coupons)' AS '';
SELECT '  - carts, cart_items' AS '';
SELECT '  - order_sequences (날짜별 주문 번호 시퀀스)' AS '';
SELECT '  - orders, order_items, order_coupons' AS '';
SELECT '  - payments (결제 정보)' AS '';
SELECT '  - user_coupons' AS '';
SELECT '  - balance_history, stock_history' AS '';
SELECT '  - product_statistics' AS '';
SELECT '  - restock_notifications' AS '';
SELECT '  - outbound_events' AS '';
SELECT '========================================' AS '';
