package com.hhplus.ecommerce.performance;

import com.hhplus.ecommerce.config.TestContainersConfig;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductStatistics;
import com.hhplus.ecommerce.infrastructure.persistence.product.ProductRepository;
import com.hhplus.ecommerce.infrastructure.persistence.product.ProductStatisticsRepository;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 인기 상품 조회 인덱스 성능 비교 테스트
 *
 * 목적:
 * - ProductStatistics 테이블의 인덱스 유무에 따른 성능 차이 측정
 * - 100만개 데이터 기준 조회 속도 비교
 *
 * 테스트 시나리오:
 * 1. 100만개 ProductStatistics 데이터 생성
 * 2. 인덱스 있는 상태에서 인기 상품 조회 (10회 반복)
 * 3. 인덱스 삭제
 * 4. 인덱스 없는 상태에서 인기 상품 조회 (10회 반복)
 * 5. 성능 비교 보고서 출력
 *
 * 인덱스:
 * - idx_statistics_date (statistics_date)
 * - idx_product_date (product_id, statistics_date)
 */
@Slf4j
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Disabled("성능 테스트 - 100만개 데이터 생성으로 인해 실행 시간이 오래 걸림. 필요시 수동 실행")
public class PopularProductIndexPerformanceTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductStatisticsRepository productStatisticsRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private static final int TOTAL_DATA_COUNT = 1_000_000; // 100만개
    private static final int PRODUCT_COUNT = 10_000; // 상품 개수
    private static final int DATE_RANGE = 100; // 날짜 범위 (일)
    private static final int BATCH_SIZE = 10_000; // 배치 크기
    private static final int TEST_ITERATIONS = 10; // 테스트 반복 횟수
    private static final int TOP_LIMIT = 5; // 인기 상품 개수

    private static List<Long> withIndexResults = new ArrayList<>();
    private static List<Long> withoutIndexResults = new ArrayList<>();
    private static List<java.util.Map<String, Object>> withIndexExplainResults = new ArrayList<>();
    private static List<java.util.Map<String, Object>> withoutIndexExplainResults = new ArrayList<>();

    /**
     * 1단계: 테스트 데이터 생성 (100만개)
     */
    //@Test
    @Order(1)
    @Transactional
    @DisplayName("1단계: 100만개 테스트 데이터 생성")
    void step1_generateTestData() {
        log.info("========================================");
        log.info("1단계: 테스트 데이터 생성 시작");
        log.info("========================================");

        long startTime = System.currentTimeMillis();

        // 기존 데이터 삭제
        log.info("기존 데이터 삭제 중...");
        productStatisticsRepository.deleteAll();
        entityManager.flush();
        entityManager.clear();

        // 테스트용 상품 10,000개 생성 (JDBC Batch Insert로 최적화)
        log.info("테스트용 상품 {} 개 생성 중 (JDBC Batch Insert)...", PRODUCT_COUNT);
        long productInsertStart = System.currentTimeMillis();

        jdbcTemplate.batchUpdate(
            "INSERT INTO products (name, description, price, stock, safety_stock, status, version, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
            new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                @Override
                public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                    ps.setString(1, "Test Product " + (i + 1));
                    ps.setString(2, "Test product for performance testing");
                    ps.setBigDecimal(3, BigDecimal.valueOf(10000 + (i * 100)));
                    ps.setInt(4, 1000);
                    ps.setInt(5, 100);
                    ps.setString(6, "AVAILABLE");
                    ps.setLong(7, 0L);
                }

                @Override
                public int getBatchSize() {
                    return PRODUCT_COUNT;
                }
            }
        );

        long productInsertEnd = System.currentTimeMillis();
        log.info("상품 생성 완료: {} 개 (소요 시간: {}ms)", PRODUCT_COUNT, productInsertEnd - productInsertStart);

        // ProductStatistics 100만개 생성 (JDBC Batch Insert로 최적화)
        log.info("ProductStatistics {} 개 생성 시작 (JDBC Batch Insert)...", TOTAL_DATA_COUNT);
        log.info("전략: {} 개 상품 × {} 일 = {} 레코드", PRODUCT_COUNT, DATE_RANGE, TOTAL_DATA_COUNT);

        long statsInsertStart = System.currentTimeMillis();
        Random random = new Random();
        LocalDate today = LocalDate.now();

        // 배치 단위로 insert (10,000개씩)
        for (int batchStart = 0; batchStart < TOTAL_DATA_COUNT; batchStart += BATCH_SIZE) {
            final int batchStartFinal = batchStart;
            final int batchEnd = Math.min(batchStart + BATCH_SIZE, TOTAL_DATA_COUNT);

            jdbcTemplate.batchUpdate(
                "INSERT INTO product_statistics (product_id, statistics_date, sales_count, sales_amount, view_count, created_at) " +
                "VALUES (?, ?, ?, ?, ?, NOW())",
                new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                        int globalIdx = batchStartFinal + i;
                        long productId = (globalIdx / DATE_RANGE) + 1; // 1부터 시작
                        int dayOffset = globalIdx % DATE_RANGE;
                        LocalDate statisticsDate = today.minusDays(dayOffset);

                        ps.setLong(1, productId);
                        ps.setObject(2, statisticsDate);
                        ps.setInt(3, random.nextInt(100) + 1);
                        ps.setBigDecimal(4, BigDecimal.valueOf(random.nextInt(1000000) + 10000));
                        ps.setInt(5, random.nextInt(1000) + 1);
                    }

                    @Override
                    public int getBatchSize() {
                        return batchEnd - batchStartFinal;
                    }
                }
            );

            double progress = (batchEnd * 100.0 / TOTAL_DATA_COUNT);
            log.info("진행률: {}/{} ({:.1f}%)", batchEnd, TOTAL_DATA_COUNT, progress);
        }

        long statsInsertEnd = System.currentTimeMillis();
        log.info("ProductStatistics 생성 완료 (소요 시간: {}ms)", statsInsertEnd - statsInsertStart);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        log.info("========================================");
        log.info("테스트 데이터 생성 완료");
        log.info("총 데이터 수: {} 개", TOTAL_DATA_COUNT);
        log.info("소요 시간: {} ms ({} 초)", duration, duration / 1000);
        log.info("========================================");
    }

    /**
     * 2단계: 인덱스 있는 상태에서 성능 측정
     */
    @Test
    @Order(2)
    @DisplayName("2단계: 인덱스 있는 상태에서 성능 측정")
    void step2_testWithIndex() {
        log.info("========================================");
        log.info("2단계: 인덱스 있는 상태에서 성능 측정");
        log.info("========================================");

        // 인덱스 존재 확인
        verifyIndexExists();

        // 워밍업 (JVM 최적화)
        log.info("워밍업 쿼리 실행 중...");
        for (int i = 0; i < 3; i++) {
            executePopularProductQuery();
        }

        // 실제 성능 측정
        log.info("성능 측정 시작 ({} 회 반복)...", TEST_ITERATIONS);
        withIndexResults.clear();

        for (int i = 1; i <= TEST_ITERATIONS; i++) {
            long duration = executePopularProductQuery();
            withIndexResults.add(duration);
            log.info("[인덱스 O] 반복 {}/{}: {} ms", i, TEST_ITERATIONS, duration);
        }

        long avgDuration = (long) withIndexResults.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);

        // EXPLAIN 결과 수집
        log.info("EXPLAIN 분석 중...");
        withIndexExplainResults.clear();
        withIndexExplainResults = getExplainResults();

        log.info("========================================");
        log.info("인덱스 있는 상태 - 평균 조회 시간: {} ms", avgDuration);
        log.info("========================================");
    }

    /**
     * 3단계: 인덱스 삭제
     */
    @Test
    @Order(3)
    @DisplayName("3단계: 인덱스 삭제")
    void step3_dropIndexes() {
        log.info("========================================");
        log.info("3단계: 인덱스 삭제");
        log.info("========================================");

        try {
            // idx_statistics_date 삭제
            jdbcTemplate.execute("DROP INDEX idx_statistics_date ON product_statistics");
            log.info("인덱스 삭제 완료: idx_statistics_date");

            // idx_product_date 삭제
            jdbcTemplate.execute("DROP INDEX idx_product_date ON product_statistics");
            log.info("인덱스 삭제 완료: idx_product_date");

            log.info("========================================");
            log.info("모든 인덱스 삭제 완료");
            log.info("========================================");
        } catch (Exception e) {
            log.error("인덱스 삭제 실패", e);
            throw new RuntimeException("인덱스 삭제 실패", e);
        }
    }

    /**
     * 4단계: 인덱스 없는 상태에서 성능 측정
     */
    @Test
    @Order(4)
    @DisplayName("4단계: 인덱스 없는 상태에서 성능 측정")
    void step4_testWithoutIndex() {
        log.info("========================================");
        log.info("4단계: 인덱스 없는 상태에서 성능 측정");
        log.info("========================================");

        // 워밍업
        log.info("워밍업 쿼리 실행 중...");
        for (int i = 0; i < 3; i++) {
            executePopularProductQuery();
        }

        // 실제 성능 측정
        log.info("성능 측정 시작 ({} 회 반복)...", TEST_ITERATIONS);
        withoutIndexResults.clear();

        for (int i = 1; i <= TEST_ITERATIONS; i++) {
            long duration = executePopularProductQuery();
            withoutIndexResults.add(duration);
            log.info("[인덱스 X] 반복 {}/{}: {} ms", i, TEST_ITERATIONS, duration);
        }

        long avgDuration = (long) withoutIndexResults.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);

        // EXPLAIN 결과 수집
        log.info("EXPLAIN 분석 중...");
        withoutIndexExplainResults.clear();
        withoutIndexExplainResults = getExplainResults();

        log.info("========================================");
        log.info("인덱스 없는 상태 - 평균 조회 시간: {} ms", avgDuration);
        log.info("========================================");
    }

    /**
     * 5단계: 성능 비교 보고서 출력
     */
    @Test
    @Order(5)
    @DisplayName("5단계: 성능 비교 보고서 출력")
    void step5_generateReport() {
        log.info("\n");
        log.info("========================================");
        log.info("인기 상품 조회 인덱스 성능 비교 보고서");
        log.info("========================================");
        log.info("");

        // 통계 계산
        long withIndexAvg = (long) withIndexResults.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);

        long withIndexMin = withIndexResults.stream()
                .mapToLong(Long::longValue)
                .min()
                .orElse(0);

        long withIndexMax = withIndexResults.stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0);

        long withoutIndexAvg = (long) withoutIndexResults.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);

        long withoutIndexMin = withoutIndexResults.stream()
                .mapToLong(Long::longValue)
                .min()
                .orElse(0);

        long withoutIndexMax = withoutIndexResults.stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0);

        double performanceImprovement = ((double) (withoutIndexAvg - withIndexAvg) / withoutIndexAvg) * 100;
        double speedupRatio = (double) withoutIndexAvg / withIndexAvg;

        // 보고서 출력
        log.info("📊 테스트 환경");
        log.info("  - 총 데이터 수: {} 개", String.format("%,d", TOTAL_DATA_COUNT));
        log.info("  - 테스트 반복 횟수: {} 회", TEST_ITERATIONS);
        log.info("  - 조회 기간: 최근 3일");
        log.info("  - 조회 개수: TOP {} 개", TOP_LIMIT);
        log.info("");

        log.info("📈 성능 측정 결과");
        log.info("");
        log.info("┌─────────────────────────────────────────────────────┐");
        log.info("│ 인덱스 있는 경우 (idx_statistics_date)              │");
        log.info("├─────────────────────────────────────────────────────┤");
        log.info("│  평균: {:>10} ms                                 │", withIndexAvg);
        log.info("│  최소: {:>10} ms                                 │", withIndexMin);
        log.info("│  최대: {:>10} ms                                 │", withIndexMax);
        log.info("└─────────────────────────────────────────────────────┘");
        log.info("");
        log.info("┌─────────────────────────────────────────────────────┐");
        log.info("│ 인덱스 없는 경우 (Full Table Scan)                 │");
        log.info("├─────────────────────────────────────────────────────┤");
        log.info("│  평균: {:>10} ms                                 │", withoutIndexAvg);
        log.info("│  최소: {:>10} ms                                 │", withoutIndexMin);
        log.info("│  최대: {:>10} ms                                 │", withoutIndexMax);
        log.info("└─────────────────────────────────────────────────────┘");
        log.info("");

        log.info("🚀 성능 개선 효과");
        log.info("  - 성능 향상률: {:.2f}%", performanceImprovement);
        log.info("  - 속도 비율: {:.2f}x 빠름", speedupRatio);
        log.info("  - 절대 시간 단축: {} ms", withoutIndexAvg - withIndexAvg);
        log.info("");

        log.info("💡 결론");
        if (performanceImprovement > 80) {
            log.info("  ✅ 인덱스가 매우 효과적입니다!");
            log.info("  ✅ {}배 이상 성능 향상", String.format("%.1f", speedupRatio));
        } else if (performanceImprovement > 50) {
            log.info("  ✅ 인덱스가 효과적입니다.");
            log.info("  ✅ {}배 성능 향상", String.format("%.1f", speedupRatio));
        } else if (performanceImprovement > 20) {
            log.info("  ⚠️ 인덱스 효과가 제한적입니다.");
        } else {
            log.info("  ❌ 인덱스 효과가 미미합니다.");
        }
        log.info("");

        log.info("📌 사용된 인덱스");
        log.info("  - idx_statistics_date: (statistics_date)");
        log.info("  - idx_product_date: (product_id, statistics_date)");
        log.info("");

        log.info("📝 쿼리 정보");
        log.info("  SELECT ps.product.id");
        log.info("  FROM ProductStatistics ps");
        log.info("  WHERE ps.statisticsDate BETWEEN :startDate AND :endDate");
        log.info("  GROUP BY ps.product.id");
        log.info("  ORDER BY SUM(ps.salesCount) DESC");
        log.info("");

        // EXPLAIN 결과 출력
        log.info("🔍 MySQL EXPLAIN 분석 결과");
        log.info("");

        if (!withIndexExplainResults.isEmpty()) {
            log.info("┌─────────────────────────────────────────────────────┐");
            log.info("│ 인덱스 있는 경우 EXPLAIN                            │");
            log.info("└─────────────────────────────────────────────────────┘");
            for (java.util.Map<String, Object> row : withIndexExplainResults) {
                log.info("  id: {}", row.get("id"));
                log.info("  select_type: {}", row.get("select_type"));
                log.info("  table: {}", row.get("table"));
                log.info("  type: {}", row.get("type"));
                log.info("  possible_keys: {}", row.get("possible_keys"));
                log.info("  key: {}", row.get("key"));
                log.info("  key_len: {}", row.get("key_len"));
                log.info("  ref: {}", row.get("ref"));
                log.info("  rows: {}", row.get("rows"));
                log.info("  Extra: {}", row.get("Extra"));
                log.info("");
            }
        }

        if (!withoutIndexExplainResults.isEmpty()) {
            log.info("┌─────────────────────────────────────────────────────┐");
            log.info("│ 인덱스 없는 경우 EXPLAIN                            │");
            log.info("└─────────────────────────────────────────────────────┘");
            for (java.util.Map<String, Object> row : withoutIndexExplainResults) {
                log.info("  id: {}", row.get("id"));
                log.info("  select_type: {}", row.get("select_type"));
                log.info("  table: {}", row.get("table"));
                log.info("  type: {}", row.get("type"));
                log.info("  possible_keys: {}", row.get("possible_keys"));
                log.info("  key: {}", row.get("key"));
                log.info("  key_len: {}", row.get("key_len"));
                log.info("  ref: {}", row.get("ref"));
                log.info("  rows: {}", row.get("rows"));
                log.info("  Extra: {}", row.get("Extra"));
                log.info("");
            }
        }

        log.info("========================================");
        log.info("보고서 생성 완료");
        log.info("========================================");
    }

    /**
     * 6단계: 인덱스 복구
     */
    @Test
    @Order(6)
    @DisplayName("6단계: 인덱스 복구")
    void step6_restoreIndexes() {
        log.info("========================================");
        log.info("6단계: 인덱스 복구");
        log.info("========================================");

        try {
            // idx_statistics_date 복구
            jdbcTemplate.execute(
                "CREATE INDEX idx_statistics_date ON product_statistics(statistics_date)"
            );
            log.info("인덱스 복구 완료: idx_statistics_date");

            // idx_product_date 복구
            jdbcTemplate.execute(
                "CREATE INDEX idx_product_date ON product_statistics(product_id, statistics_date)"
            );
            log.info("인덱스 복구 완료: idx_product_date");

            log.info("========================================");
            log.info("모든 인덱스 복구 완료");
            log.info("========================================");
        } catch (Exception e) {
            log.error("인덱스 복구 실패", e);
            throw new RuntimeException("인덱스 복구 실패", e);
        }
    }

    // ========== Helper Methods ==========

    /**
     * 인기 상품 조회 쿼리 실행 및 시간 측정
     */
    private long executePopularProductQuery() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(2); // 최근 3일

        long startTime = System.currentTimeMillis();

        List<Long> productIds = jdbcTemplate.query(
            "SELECT ps.product_id " +
            "FROM product_statistics ps " +
            "WHERE ps.statistics_date BETWEEN ? AND ? " +
            "GROUP BY ps.product_id " +
            "ORDER BY SUM(ps.sales_count) DESC " +
            "LIMIT ?",
            (rs, rowNum) -> rs.getLong("product_id"),
            startDate, endDate, TOP_LIMIT
        );

        long endTime = System.currentTimeMillis();

        return endTime - startTime;
    }

    /**
     * 인덱스 존재 확인
     */
    private void verifyIndexExists() {
        List<String> indexes = jdbcTemplate.query(
            "SHOW INDEX FROM product_statistics WHERE Key_name IN ('idx_statistics_date', 'idx_product_date')",
            (rs, rowNum) -> rs.getString("Key_name")
        );

        log.info("현재 존재하는 인덱스: {}", indexes);

        if (!indexes.contains("idx_statistics_date")) {
            throw new IllegalStateException("idx_statistics_date 인덱스가 존재하지 않습니다");
        }
    }

    /**
     * 테스트용 상품 생성
     */
    private List<Product> createTestProducts(int count) {
        List<Product> products = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            Product product = Product.builder()
                    .name("Test Product " + i)
                    .description("Test product for performance testing")
                    .price(BigDecimal.valueOf(10000 + (i * 1000)))
                    .stock(1000)
                    .safetyStock(100)
                    .status(com.hhplus.ecommerce.domain.product.ProductStatus.AVAILABLE)
                    .build();
            products.add(product);
        }

        return products;
    }

    /**
     * EXPLAIN 결과 조회
     */
    private List<java.util.Map<String, Object>> getExplainResults() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(2); // 최근 3일

        String explainQuery = "EXPLAIN SELECT ps.product_id " +
                "FROM product_statistics ps " +
                "WHERE ps.statistics_date BETWEEN ? AND ? " +
                "GROUP BY ps.product_id " +
                "ORDER BY SUM(ps.sales_count) DESC " +
                "LIMIT ?";

        return jdbcTemplate.queryForList(explainQuery, startDate, endDate, TOP_LIMIT);
    }
}
