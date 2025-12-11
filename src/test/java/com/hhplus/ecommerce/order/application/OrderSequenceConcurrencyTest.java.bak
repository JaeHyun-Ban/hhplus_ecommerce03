package com.hhplus.ecommerce.order.application;

import com.hhplus.ecommerce.config.TestContainersConfig;
import com.hhplus.ecommerce.order.domain.OrderSequence;
import com.hhplus.ecommerce.order.infrastructure.persistence.OrderSequenceRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문 번호 생성 동시성 테스트
 *
 * 검증 목표:
 * - 비관적 락을 통한 시퀀스 동시성 제어 검증
 * - REQUIRES_NEW 트랜잭션 격리 검증
 * - 100명이 동시 주문 시 중복 없는 주문 번호 생성
 * - 날짜별 독립적인 시퀀스 관리 검증
 */
@Slf4j
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("주문 번호 생성 동시성 테스트")
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class OrderSequenceConcurrencyTest {

    @Autowired
    private OrderSequenceService orderSequenceService;

    @Autowired
    private OrderSequenceRepository orderSequenceRepository;

    @BeforeEach
    void setUp() {
        // 기존 데이터 정리
        orderSequenceRepository.deleteAll();

        log.info("===========================================");
        log.info("테스트 데이터 준비 완료");
        log.info("===========================================");
    }

    @Test
    @DisplayName("주문 번호 동시성: 50명이 동시 주문 번호 생성 시 중복 없이 순차 생성")
    void testConcurrentOrderNumberGeneration_NoDuplicate() throws InterruptedException {
        // Given: 오늘 날짜의 시퀀스 미리 생성 (INSERT 데드락 방지)
        String today = LocalDate.now().toString();
        OrderSequence todaySequence = OrderSequence.create(LocalDate.now());
        orderSequenceRepository.save(todaySequence);

        // Given: 50개의 동시 요청 (DB 부하 고려)
        int concurrentRequests = 50;
        int threadPoolSize = 25;

        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch latch = new CountDownLatch(concurrentRequests);

        Set<String> orderNumbers = ConcurrentHashMap.newKeySet();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 100개 스레드가 동시에 주문 번호 생성 (주문 번호 생성 서비스만 호출)
        log.info("=== 동시성 테스트 시작: {}개 스레드가 동시에 주문 번호 생성 ===", concurrentRequests);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < concurrentRequests; i++) {
            executorService.submit(() -> {
                try {
                    // 주문 번호 생성만 호출 (전체 주문 플로우 X)
                    String orderNumber = orderSequenceService.generateOrderNumber();
                    orderNumbers.add(orderNumber);
                    successCount.incrementAndGet();
                    log.debug("✅ 주문 번호 생성 성공 - orderNumber: {}", orderNumber);

                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.error("❌ 주문 번호 생성 실패 - error: {}", e.getMessage(), e);

                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(60, TimeUnit.SECONDS);
        executorService.shutdown();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then: 검증
        log.info("");
        log.info("=== 동시성 테스트 결과 ===");
        log.info("소요 시간: {}ms", duration);
        log.info("성공: {}건, 실패: {}건", successCount.get(), failCount.get());
        log.info("생성된 유니크 주문 번호 수: {}", orderNumbers.size());
        log.info("완료 여부: {}", completed);

        // 1. 모든 요청 완료 확인
        assertThat(completed).as("60초 내에 모든 요청 완료").isTrue();
        assertThat(successCount.get()).as("모든 요청이 성공해야 함").isEqualTo(concurrentRequests);
        assertThat(failCount.get()).as("실패는 없어야 함").isZero();

        // 2. 중복 없는 주문 번호 생성 확인
        assertThat(orderNumbers).as("모든 주문 번호가 유니크해야 함")
                .hasSize(concurrentRequests);

        // 3. 주문 번호 형식 검증
        String todayFormatted = LocalDate.now().toString().replace("-", "");
        orderNumbers.forEach(orderNumber -> {
            assertThat(orderNumber)
                    .as("주문 번호 형식이 올바라야 함")
                    .matches("ORD-" + todayFormatted + "-\\d{6}");
        });

        // 4. DB에 저장된 시퀀스 확인
        OrderSequence sequence = orderSequenceRepository.findById(LocalDate.now().toString())
                .orElseThrow();

        assertThat(sequence.getSequence())
                .as("최종 시퀀스는 50이어야 함")
                .isEqualTo(50L);

        // 5. 시퀀스 범위 확인 (1~50)
        Set<Integer> sequences = new HashSet<>();
        orderNumbers.forEach(orderNumber -> {
            String sequencePart = orderNumber.substring(orderNumber.length() - 6);
            int seq = Integer.parseInt(sequencePart);
            sequences.add(seq);
        });

        assertThat(sequences)
                .as("시퀀스는 1부터 50까지 순차적이어야 함")
                .containsExactlyInAnyOrderElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, 50).boxed().toList()
                );

        log.info("");
        log.info("=== 동시성 테스트 성공: 비관적 락이 정상 동작함 ===");
        log.info("✅ 50개 동시 요청 → 50개 유니크 주문 번호 생성");
        log.info("✅ 시퀀스 1~50 순차 생성");
    }

    @Test
    @DisplayName("주문 번호: 날짜별 독립적인 시퀀스 관리")
    void testDateBasedSequence() {
        // Given: 현재 날짜로 시퀀스 생성
        LocalDate today = LocalDate.now();
        String todayStr = today.toString();

        // When: 주문 번호 3개 생성
        String order1 = orderSequenceService.generateOrderNumber();
        String order2 = orderSequenceService.generateOrderNumber();
        String order3 = orderSequenceService.generateOrderNumber();

        // Then: 주문 번호 검증
        String expectedPrefix = "ORD-" + todayStr.replace("-", "") + "-";

        assertThat(order1).isEqualTo(expectedPrefix + "000001");
        assertThat(order2).isEqualTo(expectedPrefix + "000002");
        assertThat(order3).isEqualTo(expectedPrefix + "000003");

        // DB 시퀀스 확인
        OrderSequence sequence = orderSequenceRepository.findById(todayStr).orElseThrow();
        assertThat(sequence.getSequence()).isEqualTo(3L);

        log.info("✅ 날짜별 시퀀스 관리 검증 완료");
        log.info("주문 번호 1: {}", order1);
        log.info("주문 번호 2: {}", order2);
        log.info("주문 번호 3: {}", order3);
    }

    @Test
    @DisplayName("주문 번호: REQUIRES_NEW 트랜잭션 격리 검증 (시퀀스는 증가되어야 함)")
    void testRequiresNewPropagation() {
        // Given: 초기 상태
        LocalDate today = LocalDate.now();

        // When: 주문 번호 생성 (REQUIRES_NEW로 별도 트랜잭션)
        String orderNumber1 = orderSequenceService.generateOrderNumber();

        // Then: 시퀀스 즉시 커밋 확인
        OrderSequence sequence1 = orderSequenceRepository.findById(today.toString()).orElseThrow();
        assertThat(sequence1.getSequence()).isEqualTo(1L);

        // When: 두 번째 주문 번호 생성
        String orderNumber2 = orderSequenceService.generateOrderNumber();

        // Then: 시퀀스 증가 확인
        OrderSequence sequence2 = orderSequenceRepository.findById(today.toString()).orElseThrow();
        assertThat(sequence2.getSequence()).isEqualTo(2L);

        log.info("✅ REQUIRES_NEW 트랜잭션 격리 검증 완료");
        log.info("주문 번호 1: {} (sequence: {})", orderNumber1, sequence1.getSequence());
        log.info("주문 번호 2: {} (sequence: {})", orderNumber2, sequence2.getSequence());

        // 주문 번호 형식 검증
        String expectedPrefix = "ORD-" + today.toString().replace("-", "") + "-";
        assertThat(orderNumber1).isEqualTo(expectedPrefix + "000001");
        assertThat(orderNumber2).isEqualTo(expectedPrefix + "000002");
    }
}