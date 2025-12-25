-- ================================================
-- 성능 개선을 위한 데이터베이스 인덱스 추가
-- ================================================

-- 적용 전 주의사항:
-- 1. 운영 환경에서는 피크 시간대 피해서 적용
-- 2. 인덱스 생성은 테이블 잠금을 유발할 수 있음 (ONLINE 옵션 활용)
-- 3. 대용량 테이블의 경우 시간이 오래 걸릴 수 있음
-- 4. 적용 전 반드시 백업 수행

-- ================================================
-- 1. product 테이블 인덱스
-- ================================================

-- 기존 인덱스 확인
SHOW INDEX FROM product;

-- 인덱스 1: 상품 상태별 조회 최적화
-- 사용처: ProductService.getAvailableProducts()
-- 효과: 전체 테이블 스캔 제거, WHERE status = 'AVAILABLE' 필터링 최적화
CREATE INDEX idx_product_status
ON product(status)
ALGORITHM = INPLACE LOCK = NONE;

-- 인덱스 2: 카테고리별 상품 조회 최적화 (복합 인덱스)
-- 사용처: ProductService.getProductsByCategory()
-- 효과: status + category_id 조합 조회 최적화
CREATE INDEX idx_product_status_category
ON product(status, category_id)
ALGORITHM = INPLACE LOCK = NONE;

-- 인덱스 3: 커버링 인덱스 (선택 사항)
-- 효과: 인덱스만으로 쿼리 처리 가능 (테이블 접근 불필요)
-- 주의: 인덱스 크기가 커지므로 신중히 고려
-- CREATE INDEX idx_product_status_category_covering
-- ON product(status, category_id, id, name, price, stock)
-- ALGORITHM = INPLACE LOCK = NONE;

-- ================================================
-- 2. product_statistics 테이블 인덱스
-- ================================================

-- 기존 인덱스 확인
SHOW INDEX FROM product_statistics;

-- 인덱스 1: 날짜 범위 조회 최적화
-- 사용처: ProductService.getPopularProducts() - 최근 3일 집계
-- 효과: WHERE statistics_date >= DATE_SUB(NOW(), INTERVAL 3 DAY) 최적화
CREATE INDEX idx_statistics_date
ON product_statistics(statistics_date DESC)
ALGORITHM = INPLACE LOCK = NONE;

-- 인덱스 2: 날짜 + 판매량 복합 인덱스 (정렬 최적화)
-- 사용처: 인기 상품 집계 쿼리
-- 효과: GROUP BY + ORDER BY sales_count DESC 최적화
CREATE INDEX idx_statistics_date_sales
ON product_statistics(statistics_date DESC, sales_count DESC)
ALGORITHM = INPLACE LOCK = NONE;

-- 인덱스 3: 상품별 통계 조회 최적화
-- 사용처: 특정 상품의 판매 통계 조회
-- 효과: product_id + statistics_date 조합 조회 최적화
CREATE INDEX idx_statistics_product_date
ON product_statistics(product_id, statistics_date DESC)
ALGORITHM = INPLACE LOCK = NONE;

-- ================================================
-- 3. order_product 테이블 인덱스
-- ================================================

-- 기존 인덱스 확인
SHOW INDEX FROM order_product;

-- 인덱스 1: 주문별 상품 조회 최적화
-- 사용처: OrderService.getOrder() - 주문 상세 조회
-- 효과: 주문 ID로 주문 상품 목록 조회 최적화
CREATE INDEX idx_order_product_order_id
ON order_product(order_id)
ALGORITHM = INPLACE LOCK = NONE;

-- 인덱스 2: 상품별 주문 내역 조회
-- 사용처: 상품 판매 내역 분석
CREATE INDEX idx_order_product_product_id
ON order_product(product_id)
ALGORITHM = INPLACE LOCK = NONE;

-- ================================================
-- 4. orders 테이블 인덱스
-- ================================================

-- 기존 인덱스 확인
SHOW INDEX FROM orders;

-- 인덱스 1: 사용자별 주문 목록 조회
-- 사용처: OrderService.getUserOrders()
-- 효과: user_id + created_at 조합으로 최신 주문 우선 조회
CREATE INDEX idx_order_user_created
ON orders(user_id, created_at DESC)
ALGORITHM = INPLACE LOCK = NONE;

-- 인덱스 2: 주문 상태별 조회
-- 사용처: 관리자 페이지, 주문 상태별 통계
CREATE INDEX idx_order_status_created
ON orders(status, created_at DESC)
ALGORITHM = INPLACE LOCK = NONE;

-- 인덱스 3: 주문 번호 고유 인덱스 (이미 존재할 가능성 높음)
-- 사용처: OrderService.getOrderByNumber()
-- CREATE UNIQUE INDEX idx_order_number
-- ON orders(order_number)
-- ALGORITHM = INPLACE LOCK = NONE;

-- ================================================
-- 5. user_coupon 테이블 인덱스
-- ================================================

-- 기존 인덱스 확인
SHOW INDEX FROM user_coupon;

-- 인덱스 1: 사용자별 쿠폰 조회
-- 사용처: CouponService.getMyCoupons()
CREATE INDEX idx_user_coupon_user_id
ON user_coupon(user_id, status, issued_at DESC)
ALGORITHM = INPLACE LOCK = NONE;

-- 인덱스 2: 쿠폰별 발급 내역 조회
-- 사용처: CouponService.issueCoupon() - 중복 발급 체크
CREATE INDEX idx_user_coupon_coupon_user
ON user_coupon(coupon_id, user_id)
ALGORITHM = INPLACE LOCK = NONE;

-- ================================================
-- 6. cart 테이블 인덱스
-- ================================================

-- 기존 인덱스 확인
SHOW INDEX FROM cart;

-- 인덱스 1: 사용자별 장바구니 조회
-- 사용처: CartService.getCartItems()
CREATE INDEX idx_cart_user_id
ON cart(user_id)
ALGORITHM = INPLACE LOCK = NONE;

-- ================================================
-- 7. 인덱스 생성 확인
-- ================================================

-- 각 테이블의 인덱스 확인
SELECT
    TABLE_NAME,
    INDEX_NAME,
    COLUMN_NAME,
    SEQ_IN_INDEX,
    INDEX_TYPE
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'ecommerce'
AND TABLE_NAME IN ('product', 'product_statistics', 'order_product', 'orders', 'user_coupon', 'cart')
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

-- ================================================
-- 8. 인덱스 사용률 모니터링 (적용 후)
-- ================================================

-- 슬로우 쿼리 로그 확인
SELECT * FROM mysql.slow_log ORDER BY start_time DESC LIMIT 10;

-- 인덱스 통계 확인 (MySQL 8.0+)
SELECT
    object_schema AS database_name,
    object_name AS table_name,
    index_name,
    count_star AS total_access,
    count_read AS read_operations,
    count_write AS write_operations
FROM performance_schema.table_io_waits_summary_by_index_usage
WHERE object_schema = 'ecommerce'
ORDER BY count_star DESC
LIMIT 20;

-- ================================================
-- 9. 불필요한 인덱스 제거 (선택 사항)
-- ================================================

-- 사용되지 않는 인덱스 찾기
SELECT
    object_schema,
    object_name,
    index_name
FROM performance_schema.table_io_waits_summary_by_index_usage
WHERE index_name IS NOT NULL
AND index_name != 'PRIMARY'
AND count_star = 0
AND object_schema = 'ecommerce'
ORDER BY object_schema, object_name;

-- 예시: 사용되지 않는 인덱스 제거
-- DROP INDEX idx_unused_index ON product;

-- ================================================
-- 10. 예상 효과
-- ================================================

/*
적용 전:
- 상품 목록 조회 (20개): 21개 쿼리 (1 + 20 N+1)
- 인기 상품 조회: 전체 테이블 스캔 (product_statistics)
- P95 응답 시간: 620ms

적용 후:
- 상품 목록 조회: 1개 쿼리 (인덱스 + Fetch Join)
- 인기 상품 조회: 인덱스 범위 스캔
- P95 응답 시간: 280ms (-55%)

추가 캐시 적용 후:
- P95 응답 시간: 50ms (-92%, 캐시 히트 80%+)
- 처리량: 85 TPS → 600 TPS (+606%)
*/

-- ================================================
-- 완료!
-- ================================================

SELECT '인덱스 생성 완료!' AS message;
SELECT '다음 단계: EXPLAIN ANALYZE로 쿼리 실행 계획 확인' AS next_step;
