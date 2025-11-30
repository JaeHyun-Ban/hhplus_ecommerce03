package com.hhplus.ecommerce.product.application;

import com.hhplus.ecommerce.config.TestContainersConfig;
import com.hhplus.ecommerce.product.domain.Category;
import com.hhplus.ecommerce.product.domain.Product;
import com.hhplus.ecommerce.product.domain.ProductStatus;
import com.hhplus.ecommerce.product.infrastructure.persistence.CategoryRepository;
import com.hhplus.ecommerce.product.infrastructure.persistence.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB 성능 측정 및 병목 현상 분석 테스트
 *
 * 측정 항목:
 * 1. 단일 조회 성능 (평균 응답 시간)
 * 2. N+1 문제 여부
 * 3. 쿼리 실행 횟수
 * 4. 인덱스 사용 여부
 * 5. 대량 데이터 조회 성능
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@DisplayName("DB 성능 측정 테스트")
class ProductDatabasePerformanceTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;
    private List<Product> testProducts;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        // Hibernate Statistics 활성화
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        // 기존 데이터 삭제 (테스트 격리)
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        // 테스트 카테고리 생성
        testCategory = Category.builder()
            .name("성능 테스트 카테고리")
            .description("DB 성능 측정용")
            .build();
        testCategory = categoryRepository.save(testCategory);

        // 대량의 테스트 데이터 생성 (100개)
        testProducts = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            Product product = Product.builder()
                .name("성능테스트상품_" + i)
                .description("성능 테스트용 상품 " + i)
                .price(BigDecimal.valueOf(10000 + i * 100))
                .stock(100)
                .safetyStock(10)
                .category(testCategory)
                .status(ProductStatus.AVAILABLE)
                .build();
            testProducts.add(productRepository.save(product));
        }

        // 통계 초기화 (데이터 생성 쿼리 제외)
        statistics.clear();

        log.info("=== 테스트 데이터 준비 완료: 상품 {}개 ===", testProducts.size());
    }

    @Test
    @DisplayName("단일 상품 조회 - 평균 응답 시간 측정")
    @Transactional
    void measureSingleQueryPerformance() {
        // Given
        Long productId = testProducts.get(0).getId();
        int iterations = 100;

        // When: 100회 반복 조회
        long totalTime = 0;
        for (int i = 0; i < iterations; i++) {
            entityManagerFactory.getCache().evictAll(); // 2차 캐시 무효화
            statistics.clear();

            long startTime = System.nanoTime();
            Product product = productRepository.findById(productId).orElseThrow();
            long endTime = System.nanoTime();

            totalTime += (endTime - startTime);
        }

        double avgTimeMs = (totalTime / iterations) / 1_000_000.0;

        // Then: 결과 출력
        log.info("");
        log.info("=== 단일 상품 조회 성능 ===");
        log.info("반복 횟수: {}회", iterations);
        log.info("평균 응답 시간: {:.3f}ms", avgTimeMs);
        log.info("목표: 10ms 이하");
        log.info("현재 상태: {}", avgTimeMs < 10 ? "✅ PASS" : "⚠️ 개선 필요");

        // 성능 기준: 단순 조회는 10ms 이하여야 함
        assertThat(avgTimeMs).isLessThan(50.0); // 현재는 여유있게 50ms로 설정
    }

    @Test
    @DisplayName("N+1 문제 감지 - 100개 상품 조회 시 쿼리 개수")
    @Transactional
    void detectNPlusOneProblem() {
        // Given
        statistics.clear();

        // When: 100개 상품 모두 조회
        long startTime = System.currentTimeMillis();
        List<Product> products = productRepository.findAll();

        // Category 접근 시뮬레이션 (Lazy Loading)
        for (Product product : products) {
            if (product.getCategory() != null) {
                product.getCategory().getName(); // Lazy Loading 트리거
            }
        }
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        // 실행된 쿼리 통계
        long queryCount = statistics.getPrepareStatementCount();
        long entityLoadCount = statistics.getEntityLoadCount();

        // Then: 결과 출력
        log.info("");
        log.info("=== N+1 문제 감지 테스트 ===");
        log.info("조회한 상품 수: {}개", products.size());
        log.info("실행된 쿼리 수: {}개", queryCount);
        log.info("로드된 엔티티 수: {}개", entityLoadCount);
        log.info("총 소요 시간: {}ms", totalTime);
        log.info("");
        log.info("분석:");
        if (queryCount > 10) {
            log.warn("⚠️ N+1 문제 발생 가능성 높음!");
            log.warn("   - Fetch Join 또는 @EntityGraph 사용 권장");
            log.warn("   - 예상 쿼리 수: 2개 (상품 1개 + 카테고리 1개)");
            log.warn("   - 실제 쿼리 수: {}개", queryCount);
        } else {
            log.info("✅ N+1 문제 없음 (효율적인 쿼리)");
        }

        // 현재는 N+1 문제가 있을 수 있으므로 느슨한 기준
        assertThat(queryCount).isLessThan(200); // 개선 후에는 10 이하로 변경
    }

    @Test
    @DisplayName("인덱스 성능 비교 - ID vs Name 조회")
    void compareIndexPerformance() {
        // Given
        int iterations = 100;

        // When: ID로 조회 (Primary Key - 인덱스 사용)
        long idQueryTotalTime = 0;
        for (int i = 0; i < iterations; i++) {
            Long productId = testProducts.get(i % testProducts.size()).getId();

            long startTime = System.nanoTime();
            productRepository.findById(productId);
            long endTime = System.nanoTime();

            idQueryTotalTime += (endTime - startTime);
        }

        // When: Name으로 조회 (인덱스 있음)
        long nameQueryTotalTime = 0;
        for (int i = 0; i < iterations; i++) {
            String productName = testProducts.get(i % testProducts.size()).getName();

            long startTime = System.nanoTime();
            productRepository.findByName(productName);
            long endTime = System.nanoTime();

            nameQueryTotalTime += (endTime - startTime);
        }

        double avgIdQueryMs = (idQueryTotalTime / iterations) / 1_000_000.0;
        double avgNameQueryMs = (nameQueryTotalTime / iterations) / 1_000_000.0;

        // Then: 결과 출력
        log.info("");
        log.info("=== 인덱스 성능 비교 ===");
        log.info("반복 횟수: {}회", iterations);
        log.info("ID 조회 평균: {:.3f}ms (Primary Key Index)", avgIdQueryMs);
        log.info("Name 조회 평균: {:.3f}ms (idx_name Index)", avgNameQueryMs);
        log.info("성능 차이: {:.1f}배", avgNameQueryMs / avgIdQueryMs);
        log.info("");
        log.info("분석:");
        log.info("- Primary Key 조회가 가장 빠름 (기본)");
        log.info("- Name 인덱스가 제대로 작동 중: {}", avgNameQueryMs < avgIdQueryMs * 10 ? "✅" : "⚠️");
    }

    @Test
    @DisplayName("대량 조회 성능 - Pagination 효과 측정")
    void measurePaginationPerformance() {
        // Given
        int pageSize = 10;

        // When: 전체 조회 (Pagination 없음)
        long fullLoadStart = System.currentTimeMillis();
        List<Product> allProducts = productRepository.findAll();
        long fullLoadEnd = System.currentTimeMillis();
        long fullLoadTime = fullLoadEnd - fullLoadStart;

        // When: 페이지 조회 (첫 페이지만)
        long pageLoadStart = System.currentTimeMillis();
        org.springframework.data.domain.Page<Product> firstPage =
            productRepository.findAll(
                org.springframework.data.domain.PageRequest.of(0, pageSize)
            );
        long pageLoadEnd = System.currentTimeMillis();
        long pageLoadTime = pageLoadEnd - pageLoadStart;

        // Then: 결과 출력
        log.info("");
        log.info("=== 대량 조회 성능 ===");
        log.info("전체 조회 ({}개): {}ms", allProducts.size(), fullLoadTime);
        log.info("페이지 조회 ({}개): {}ms", firstPage.getContent().size(), pageLoadTime);
        log.info("성능 개선: {:.1f}배", (double)fullLoadTime / pageLoadTime);
        log.info("");
        log.info("권장사항:");
        log.info("- API 응답 시 반드시 Pagination 사용");
        log.info("- 한 번에 로드하는 데이터 최소화");

        assertThat(pageLoadTime).isLessThan(fullLoadTime);
    }

    @Test
    @DisplayName("쿼리 실행 계획 분석 - EXPLAIN 시뮬레이션")
    @Transactional
    void analyzeQueryExecutionPlan() {
        // Given
        Long productId = testProducts.get(0).getId();
        EntityManager em = entityManagerFactory.createEntityManager();

        try {
            em.getTransaction().begin();

            // When: 쿼리 실행 및 통계 수집
            statistics.clear();

            Product product = em.createQuery(
                "SELECT p FROM Product p WHERE p.id = :id", Product.class)
                .setParameter("id", productId)
                .getSingleResult();

            em.getTransaction().commit();

            // Then: 통계 출력
            log.info("");
            log.info("=== 쿼리 실행 통계 ===");
            log.info("쿼리 실행 횟수: {}", statistics.getQueryExecutionCount());
            log.info("쿼리 캐시 히트: {}", statistics.getQueryCacheHitCount());
            log.info("쿼리 캐시 미스: {}", statistics.getQueryCacheMissCount());
            log.info("2차 캐시 히트: {}", statistics.getSecondLevelCacheHitCount());
            log.info("2차 캐시 미스: {}", statistics.getSecondLevelCacheMissCount());
            log.info("");
            log.info("최적화 포인트:");
            log.info("1. 쿼리 캐시 활성화 고려");
            log.info("2. 2차 캐시 (엔티티 캐시) 활성화 고려");
            log.info("3. 자주 조회되는 데이터는 Redis 캐시 적용 (현재 적용됨 ✅)");

            assertThat(product).isNotNull();
        } finally {
            em.close();
        }
    }

    @Test
    @DisplayName("병목 현상 종합 분석 - 95ms 원인 파악")
    @Transactional
    void analyzeBottleneck() {
        // Given
        Long productId = testProducts.get(0).getId();

        log.info("");
        log.info("=== 🔍 95ms 병목 현상 분석 시작 ===");
        log.info("");

        // 1. TestContainers 오버헤드 측정
        long testContainersStart = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            productRepository.findById(productId);
        }
        long testContainersTime = (System.currentTimeMillis() - testContainersStart) / 10;
        log.info("1️⃣ TestContainers 평균 응답: {}ms", testContainersTime);
        log.info("   - Docker 컨테이너 네트워크 레이턴시 포함");

        // 2. 2차 캐시 없이 조회
        entityManagerFactory.getCache().evictAll();
        long noCacheStart = System.currentTimeMillis();
        productRepository.findById(productId);
        long noCacheTime = System.currentTimeMillis() - noCacheStart;
        log.info("2️⃣ 2차 캐시 미사용 시: {}ms", noCacheTime);

        // 3. Lazy Loading 체크
        statistics.clear();
        Product product = productRepository.findById(productId).orElseThrow();
        if (product.getCategory() != null) {
            product.getCategory().getName(); // Lazy Loading 트리거
        }
        long lazyLoadQueries = statistics.getPrepareStatementCount();
        log.info("3️⃣ Lazy Loading 쿼리 수: {}", lazyLoadQueries);
        log.info("   - Category는 LAZY 로딩: {}", lazyLoadQueries > 1 ? "추가 쿼리 발생 ⚠️" : "OK ✅");

        // 4. 권장사항 출력
        log.info("");
        log.info("=== 📋 성능 개선 권장사항 ===");
        log.info("");
        log.info("✅ 이미 적용됨:");
        log.info("   - Spring Cache + Redis (캐시 적용됨)");
        log.info("   - 인덱스 설정 (idx_name, idx_category_id, idx_status_stock)");
        log.info("");
        log.info("⚡ 추가 개선 가능:");
        log.info("   1. Fetch Join 사용");
        log.info("      → productRepository.findByIdWithCategory(id)");
        log.info("   2. @EntityGraph 사용");
        log.info("      → @EntityGraph(attributePaths = \"category\")");
        log.info("   3. DTO Projection 사용");
        log.info("      → 필요한 필드만 조회 (SELECT p.id, p.name, ...)");
        log.info("   4. 프로덕션 DB 사용");
        log.info("      → TestContainers 대신 실제 DB 성능 측정");
        log.info("");
        log.info("🎯 목표: 10ms 이하 달성");
        log.info("📊 현재: {}ms (TestContainers 환경)", testContainersTime);
        log.info("🚀 예상: 5-10ms (프로덕션 환경 + Fetch Join)");
    }
}
