package com.hhplus.ecommerce.product.application;

import com.hhplus.ecommerce.config.TestContainersConfig;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductStatisticsRepository;
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
import java.util.*;

/**
 * 대용량 데이터 인덱스 성능 비교 테스트 (1000만개)
 *
 * 목적:
 * - 대용량 환경에서 인덱스 효과 측정
 * - 다양한 날짜 범위별 성능 비교
 *
 * 테스트 시나리오:
 * 1. 1000만개 ProductStatistics 데이터 생성
 * 2. 다양한 날짜 범위로 테스트 (3일, 30일, 90일)
 * 3. 각 범위별 인덱스 O/X 성능 비교
 */
@Slf4j
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Disabled("성능 테스트 - 1000만개 데이터 생성으로 인해 실행 시간이 매우 오래 걸림. 필요시 수동 실행")
public class LargeScaleIndexPerformanceTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductStatisticsRepository productStatisticsRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private static final int TOTAL_DATA_COUNT = 10_000_000; // 1000만개
    private static final int PRODUCT_COUNT = 100_000; // 10만개 상품
    private static final int DATE_RANGE = 100; // 100일
    private static final int BATCH_SIZE = 10_000;
    private static final int TEST_ITERATIONS = 10;

    // 테스트할 날짜 범위들
    private static final int[] DAY_RANGES = {3, 30, 90}; // 3일, 30일, 90일

    // 결과 저장
    private static Map<String, Map<Integer, List<Long>>> results = new LinkedHashMap<>();

    static {
        results.put("WITH_INDEX", new LinkedHashMap<>());
        results.put("WITHOUT_INDEX", new LinkedHashMap<>());
    }

    /**
     * 1단계: 1000만개 테스트 데이터 생성
     */
    @Test
    @Order(1)
    @Transactional
    @DisplayName("1단계: 1000만개 테스트 데이터 생성")
    void step1_generateTestData() {
        log.info("========================================");
        log.info("1단계: 대용량 테스트 데이터 생성 시작");
        log.info("총 데이터: {} 개", String.format("%,d", TOTAL_DATA_COUNT));
        log.info("========================================");

        long startTime = System.currentTimeMillis();

        // 기존 데이터 삭제
        log.info("기존 데이터 삭제 중...");
        productStatisticsRepository.deleteAll();
        entityManager.flush();
        entityManager.clear();

        // 상품 100,000개 생성
        log.info("테스트용 상품 {} 개 생성 중 (JDBC Batch Insert)...", String.format("%,d", PRODUCT_COUNT));
        long productInsertStart = System.currentTimeMillis();

        jdbcTemplate.batchUpdate(
            "INSERT INTO products (name, description, price, stock, safety_stock, status, version, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
            new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                @Override
                public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                    ps.setString(1, "Product " + (i + 1));
                    ps.setString(2, "Performance test product");
                    ps.setBigDecimal(3, BigDecimal.valueOf(10000 + (i * 10)));
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
        log.info("상품 생성 완료: {} 개 (소요: {}초)",
                 String.format("%,d", PRODUCT_COUNT),
                 (productInsertEnd - productInsertStart) / 1000);

        // ProductStatistics 1000만개 생성
        log.info("ProductStatistics {} 개 생성 시작...", String.format("%,d", TOTAL_DATA_COUNT));
        log.info("전략: {} 상품 × {} 일 = {} 레코드",
                 String.format("%,d", PRODUCT_COUNT), DATE_RANGE, String.format("%,d", TOTAL_DATA_COUNT));

        long statsInsertStart = System.currentTimeMillis();
        Random random = new Random();
        LocalDate today = LocalDate.now();

        // 배치 단위로 insert
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
                        long productId = (globalIdx / DATE_RANGE) + 1;
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
            if (batchEnd % (BATCH_SIZE * 10) == 0) {
                long elapsed = System.currentTimeMillis() - statsInsertStart;
                long estimated = (long) (elapsed / progress * 100);
                log.info("진행률: {}/{} ({:.1f}%) - 경과: {}초, 예상: {}초",
                         String.format("%,d", batchEnd),
                         String.format("%,d", TOTAL_DATA_COUNT),
                         progress,
                         elapsed / 1000,
                         estimated / 1000);
            }
        }

        long statsInsertEnd = System.currentTimeMillis();
        log.info("ProductStatistics 생성 완료 (소요: {}분)", (statsInsertEnd - statsInsertStart) / 60000);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        log.info("========================================");
        log.info("테스트 데이터 생성 완료");
        log.info("총 데이터 수: {} 개", String.format("%,d", TOTAL_DATA_COUNT));
        log.info("총 소요 시간: {}분 {}초", duration / 60000, (duration % 60000) / 1000);
        log.info("========================================");
    }

    /**
     * 2단계: 인덱스 있는 상태에서 다양한 날짜 범위 테스트
     */
    @Test
    @Order(2)
    @DisplayName("2단계: 인덱스 있는 상태 - 다양한 날짜 범위 테스트")
    void step2_testWithIndexVariousRanges() {
        log.info("========================================");
        log.info("2단계: 인덱스 있는 상태 - 다양한 날짜 범위 테스트");
        log.info("========================================");

        verifyIndexExists();

        for (int days : DAY_RANGES) {
            log.info("\n[인덱스 O] 최근 {} 일 범위 테스트", days);

            // 워밍업
            for (int i = 0; i < 3; i++) {
                executePopularProductQuery(days);
            }

            // 실제 측정
            List<Long> durations = new ArrayList<>();
            for (int i = 1; i <= TEST_ITERATIONS; i++) {
                long duration = executePopularProductQuery(days);
                durations.add(duration);
                log.info("  반복 {}/{}: {} ms", i, TEST_ITERATIONS, duration);
            }

            results.get("WITH_INDEX").put(days, durations);

            long avg = (long) durations.stream().mapToLong(Long::longValue).average().orElse(0);
            log.info("[인덱스 O] 최근 {}일 - 평균: {} ms", days, avg);
        }
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
            jdbcTemplate.execute("DROP INDEX idx_statistics_date ON product_statistics");
            log.info("인덱스 삭제 완료: idx_statistics_date");

            jdbcTemplate.execute("DROP INDEX idx_product_date ON product_statistics");
            log.info("인덱스 삭제 완료: idx_product_date");

            log.info("모든 인덱스 삭제 완료");
        } catch (Exception e) {
            log.error("인덱스 삭제 실패", e);
            throw new RuntimeException("인덱스 삭제 실패", e);
        }
    }

    /**
     * 4단계: 인덱스 없는 상태에서 다양한 날짜 범위 테스트
     */
    @Test
    @Order(4)
    @DisplayName("4단계: 인덱스 없는 상태 - 다양한 날짜 범위 테스트")
    void step4_testWithoutIndexVariousRanges() {
        log.info("========================================");
        log.info("4단계: 인덱스 없는 상태 - 다양한 날짜 범위 테스트");
        log.info("========================================");

        for (int days : DAY_RANGES) {
            log.info("\n[인덱스 X] 최근 {} 일 범위 테스트", days);

            // 워밍업
            for (int i = 0; i < 3; i++) {
                executePopularProductQuery(days);
            }

            // 실제 측정
            List<Long> durations = new ArrayList<>();
            for (int i = 1; i <= TEST_ITERATIONS; i++) {
                long duration = executePopularProductQuery(days);
                durations.add(duration);
                log.info("  반복 {}/{}: {} ms", i, TEST_ITERATIONS, duration);
            }

            results.get("WITHOUT_INDEX").put(days, durations);

            long avg = (long) durations.stream().mapToLong(Long::longValue).average().orElse(0);
            log.info("[인덱스 X] 최근 {}일 - 평균: {} ms", days, avg);
        }
    }

    /**
     * 5단계: 종합 보고서 생성
     */
    @Test
    @Order(5)
    @DisplayName("5단계: 종합 성능 비교 보고서")
    void step5_generateComprehensiveReport() {
        log.info("\n");
        log.info("========================================");
        log.info("대용량 데이터 인덱스 성능 비교 종합 보고서");
        log.info("========================================");
        log.info("");

        log.info("📊 테스트 환경");
        log.info("  - 총 데이터 수: {} 개", String.format("%,d", TOTAL_DATA_COUNT));
        log.info("  - 상품 수: {} 개", String.format("%,d", PRODUCT_COUNT));
        log.info("  - 날짜 범위: {} 일", DATE_RANGE);
        log.info("  - 테스트 반복: {} 회", TEST_ITERATIONS);
        log.info("");

        log.info("📈 성능 측정 결과");
        log.info("");

        for (int days : DAY_RANGES) {
            List<Long> withIndex = results.get("WITH_INDEX").get(days);
            List<Long> withoutIndex = results.get("WITHOUT_INDEX").get(days);

            if (withIndex == null || withoutIndex == null) continue;

            long withIndexAvg = (long) withIndex.stream().mapToLong(Long::longValue).average().orElse(0);
            long withIndexMin = withIndex.stream().mapToLong(Long::longValue).min().orElse(0);
            long withIndexMax = withIndex.stream().mapToLong(Long::longValue).max().orElse(0);

            long withoutIndexAvg = (long) withoutIndex.stream().mapToLong(Long::longValue).average().orElse(0);
            long withoutIndexMin = withoutIndex.stream().mapToLong(Long::longValue).min().orElse(0);
            long withoutIndexMax = withoutIndex.stream().mapToLong(Long::longValue).max().orElse(0);

            int recordCount = PRODUCT_COUNT * days;
            double selectivity = (recordCount * 100.0 / TOTAL_DATA_COUNT);

            log.info("┌─────────────────────────────────────────────────────┐");
            log.info("│ 최근 {} 일 범위 (레코드: {:,}개, 선택도: {:.1f}%)      │",
                     days, recordCount, selectivity);
            log.info("├─────────────────────────────────────────────────────┤");
            log.info("│ [인덱스 O] 평균: {:>6} ms (최소: {} ms, 최대: {} ms) │",
                     withIndexAvg, withIndexMin, withIndexMax);
            log.info("│ [인덱스 X] 평균: {:>6} ms (최소: {} ms, 최대: {} ms) │",
                     withoutIndexAvg, withoutIndexMin, withoutIndexMax);
            log.info("├─────────────────────────────────────────────────────┤");

            long diff = withoutIndexAvg - withIndexAvg;
            double improvement = withoutIndexAvg > 0 ? ((double) diff / withoutIndexAvg) * 100 : 0;
            double speedup = withIndexAvg > 0 ? (double) withoutIndexAvg / withIndexAvg : 0;

            log.info("│ 성능 향상: {} ms ({:.1f}% 개선, {:.1f}x 빠름)           │",
                     diff, improvement, speedup);
            log.info("└─────────────────────────────────────────────────────┘");
            log.info("");
        }

        log.info("💡 결론");
        log.info("");

        // 90일 범위 기준으로 결론 도출
        List<Long> withIndex90 = results.get("WITH_INDEX").get(90);
        List<Long> withoutIndex90 = results.get("WITHOUT_INDEX").get(90);

        if (withIndex90 != null && withoutIndex90 != null) {
            long withIndexAvg = (long) withIndex90.stream().mapToLong(Long::longValue).average().orElse(0);
            long withoutIndexAvg = (long) withoutIndex90.stream().mapToLong(Long::longValue).average().orElse(0);
            double improvement = withoutIndexAvg > 0 ? ((double) (withoutIndexAvg - withIndexAvg) / withoutIndexAvg) * 100 : 0;

            if (improvement > 80) {
                log.info("  ✅ 인덱스가 매우 효과적입니다! (90일 기준 {:.1f}% 개선)", improvement);
            } else if (improvement > 50) {
                log.info("  ✅ 인덱스가 효과적입니다. (90일 기준 {:.1f}% 개선)", improvement);
            } else if (improvement > 20) {
                log.info("  ⚠️ 인덱스 효과가 제한적입니다. (90일 기준 {:.1f}% 개선)", improvement);
            } else {
                log.info("  ❌ 인덱스 효과가 미미합니다. (90일 기준 {:.1f}% 개선)", improvement);
            }
        }

        log.info("");
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
            jdbcTemplate.execute(
                "CREATE INDEX idx_statistics_date ON product_statistics(statistics_date)"
            );
            log.info("인덱스 복구 완료: idx_statistics_date");

            jdbcTemplate.execute(
                "CREATE INDEX idx_product_date ON product_statistics(product_id, statistics_date)"
            );
            log.info("인덱스 복구 완료: idx_product_date");

            log.info("모든 인덱스 복구 완료");
        } catch (Exception e) {
            log.error("인덱스 복구 실패", e);
            throw new RuntimeException("인덱스 복구 실패", e);
        }
    }

    // ========== Helper Methods ==========

    private long executePopularProductQuery(int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        long startTime = System.currentTimeMillis();

        jdbcTemplate.query(
            "SELECT ps.product_id " +
            "FROM product_statistics ps " +
            "WHERE ps.statistics_date BETWEEN ? AND ? " +
            "GROUP BY ps.product_id " +
            "ORDER BY SUM(ps.sales_count) DESC " +
            "LIMIT 5",
            (rs, rowNum) -> rs.getLong("product_id"),
            startDate, endDate
        );

        long endTime = System.currentTimeMillis();

        return endTime - startTime;
    }

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
}
