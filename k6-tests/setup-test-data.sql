-- ================================================
-- E-Commerce 부하 테스트용 데이터 생성 스크립트
-- ================================================

-- 1. 기존 테스트 데이터 정리 (선택 사항)
-- DELETE FROM user_coupon WHERE user_id BETWEEN 1 AND 1000;
-- DELETE FROM coupon WHERE id BETWEEN 1 AND 10;
-- DELETE FROM product WHERE id BETWEEN 1 AND 100;
-- DELETE FROM category WHERE id BETWEEN 1 AND 10;
-- DELETE FROM users WHERE id BETWEEN 1 AND 1000;

-- ================================================
-- 1. 카테고리 데이터 (5개)
-- ================================================
INSERT INTO category (id, name, description, created_at, updated_at) VALUES
(1, '전자제품', '각종 전자제품 카테고리', NOW(), NOW()),
(2, '의류', '패션 및 의류 카테고리', NOW(), NOW()),
(3, '식품', '식품 및 음료 카테고리', NOW(), NOW()),
(4, '도서', '도서 및 출판물 카테고리', NOW(), NOW()),
(5, '생활용품', '생활 용품 카테고리', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    updated_at = NOW();

-- ================================================
-- 2. 사용자 데이터 (1000명)
-- ================================================
-- 프로시저를 사용하여 1000명의 사용자 생성
DELIMITER //

DROP PROCEDURE IF EXISTS create_test_users;

CREATE PROCEDURE create_test_users()
BEGIN
    DECLARE i INT DEFAULT 1;

    WHILE i <= 1000 DO
        INSERT INTO users (id, name, email, balance, created_at, updated_at)
        VALUES (
            i,
            CONCAT('테스트사용자', i),
            CONCAT('test', i, '@example.com'),
            100000.00,  -- 초기 잔액 10만원
            NOW(),
            NOW()
        )
        ON DUPLICATE KEY UPDATE
            balance = 100000.00,
            updated_at = NOW();

        SET i = i + 1;
    END WHILE;
END //

DELIMITER ;

-- 프로시저 실행
CALL create_test_users();

-- ================================================
-- 3. 상품 데이터 (100개)
-- ================================================
DELIMITER //

DROP PROCEDURE IF EXISTS create_test_products;

CREATE PROCEDURE create_test_products()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE cat_id INT;

    WHILE i <= 100 DO
        -- 카테고리를 순환하면서 할당 (1~5)
        SET cat_id = ((i - 1) MOD 5) + 1;

        INSERT INTO product (id, category_id, name, description, price, stock, status, created_at, updated_at)
        VALUES (
            i,
            cat_id,
            CONCAT('테스트상품 ', i),
            CONCAT('테스트용 상품 ', i, '번 상세 설명'),
            (i * 1000) + 9000,  -- 가격: 10,000 ~ 109,000원
            1000,  -- 재고: 1000개
            'AVAILABLE',
            NOW(),
            NOW()
        )
        ON DUPLICATE KEY UPDATE
            stock = 1000,
            status = 'AVAILABLE',
            updated_at = NOW();

        SET i = i + 1;
    END WHILE;
END //

DELIMITER ;

-- 프로시저 실행
CALL create_test_products();

-- ================================================
-- 4. 쿠폰 데이터 (선착순 테스트용)
-- ================================================
INSERT INTO coupon (
    id, code, name, description,
    discount_type, discount_value,
    minimum_order_amount, maximum_discount_amount,
    total_quantity, issued_quantity,
    max_issue_per_user,
    issue_start_at, issue_end_at,
    valid_from, valid_until,
    status, created_at, updated_at
) VALUES (
    1,
    'LOAD_TEST_100',
    '부하테스트용 쿠폰 (100개 한정)',
    '선착순 100명 10% 할인 쿠폰',
    'PERCENTAGE',
    10.00,
    10000.00,
    5000.00,
    100,  -- 총 수량: 100개
    0,    -- 발급 수량: 0개 (초기값)
    1,    -- 사용자당 최대 1개
    DATE_SUB(NOW(), INTERVAL 1 DAY),  -- 발급 시작: 어제
    DATE_ADD(NOW(), INTERVAL 30 DAY), -- 발급 종료: 30일 후
    DATE_SUB(NOW(), INTERVAL 1 DAY),  -- 사용 시작: 어제
    DATE_ADD(NOW(), INTERVAL 60 DAY), -- 사용 종료: 60일 후
    'ACTIVE',
    NOW(),
    NOW()
),
(
    2,
    'LOAD_TEST_500',
    '부하테스트용 쿠폰 (500개)',
    '선착순 500명 15% 할인 쿠폰',
    'PERCENTAGE',
    15.00,
    20000.00,
    10000.00,
    500,  -- 총 수량: 500개
    0,
    1,
    DATE_SUB(NOW(), INTERVAL 1 DAY),
    DATE_ADD(NOW(), INTERVAL 30 DAY),
    DATE_SUB(NOW(), INTERVAL 1 DAY),
    DATE_ADD(NOW(), INTERVAL 60 DAY),
    'ACTIVE',
    NOW(),
    NOW()
)
ON DUPLICATE KEY UPDATE
    issued_quantity = 0,
    status = 'ACTIVE',
    updated_at = NOW();

-- ================================================
-- 5. 장바구니 데이터 (각 사용자별 랜덤 상품 1~3개)
-- ================================================
DELIMITER //

DROP PROCEDURE IF EXISTS create_test_carts;

CREATE PROCEDURE create_test_carts()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE product_count INT;
    DECLARE j INT;
    DECLARE rand_product_id INT;
    DECLARE rand_quantity INT;

    WHILE i <= 100 DO  -- 처음 100명의 사용자만 장바구니 생성
        -- 각 사용자는 1~3개의 상품을 장바구니에 추가
        SET product_count = FLOOR(1 + (RAND() * 3));
        SET j = 0;

        WHILE j < product_count DO
            SET rand_product_id = FLOOR(1 + (RAND() * 100));  -- 1~100번 상품
            SET rand_quantity = FLOOR(1 + (RAND() * 3));      -- 수량 1~3개

            INSERT INTO cart (user_id, product_id, quantity, created_at, updated_at)
            VALUES (
                i,
                rand_product_id,
                rand_quantity,
                NOW(),
                NOW()
            )
            ON DUPLICATE KEY UPDATE
                quantity = rand_quantity,
                updated_at = NOW();

            SET j = j + 1;
        END WHILE;

        SET i = i + 1;
    END WHILE;
END //

DELIMITER ;

-- 프로시저 실행
CALL create_test_carts();

-- ================================================
-- 6. Redis 초기화 (선택 사항)
-- ================================================
-- Redis CLI에서 실행:
-- redis-cli FLUSHALL

-- ================================================
-- 7. 데이터 확인
-- ================================================
SELECT '=== 카테고리 ===' AS '';
SELECT COUNT(*) AS category_count FROM category;

SELECT '=== 사용자 ===' AS '';
SELECT COUNT(*) AS user_count FROM users;

SELECT '=== 상품 ===' AS '';
SELECT COUNT(*) AS product_count, SUM(stock) AS total_stock FROM product;

SELECT '=== 쿠폰 ===' AS '';
SELECT id, code, name, total_quantity, issued_quantity, status FROM coupon;

SELECT '=== 장바구니 ===' AS '';
SELECT COUNT(*) AS cart_item_count, COUNT(DISTINCT user_id) AS users_with_cart FROM cart;

-- ================================================
-- 8. 정리 프로시저 삭제
-- ================================================
DROP PROCEDURE IF EXISTS create_test_users;
DROP PROCEDURE IF EXISTS create_test_products;
DROP PROCEDURE IF EXISTS create_test_carts;

-- ================================================
-- 완료!
-- ================================================
SELECT '테스트 데이터 생성 완료!' AS message;
