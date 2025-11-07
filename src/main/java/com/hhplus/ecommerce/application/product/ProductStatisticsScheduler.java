package com.hhplus.ecommerce.application.product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 상품 통계 스케줄러
 *
 * Infrastructure Layer - 배치 작업 스케줄링
 *
 * 책임:
 * - 일일 상품 통계 집계 배치 작업 실행
 * - 매일 정해진 시간에 자동 실행
 *
 * 실행 시점:
 * - 매일 새벽 1시 (KST)
 * - Cron: "0 0 1 * * *"
 *   - 초: 0
 *   - 분: 0
 *   - 시: 1 (새벽 1시)
 *   - 일: * (매일)
 *   - 월: * (매월)
 *   - 요일: * (모든 요일)
 *
 * 집계 대상:
 * - 전일(D-1) 데이터
 * - 예: 11월 7일 새벽 1시 → 11월 6일 주문 데이터 집계
 *
 * 실행 환경:
 * - @EnableScheduling 활성화 필요 (SchedulerConfig)
 * - 단일 인스턴스 환경에서만 실행 권장
 * - 다중 인스턴스 환경에서는 분산 락 필요 (ShedLock 등)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductStatisticsScheduler {

    private final ProductStatisticsService productStatisticsService;

    /**
     * 일일 상품 통계 집계 배치 작업
     *
     * 실행 시간:
     * - 매일 새벽 1시 (KST)
     *
     * 처리 내용:
     * - 전일(D-1) 주문 데이터 집계
     * - 상품별 판매량, 판매금액 계산
     * - ProductStatistics 테이블 저장
     *
     * 예외 처리:
     * - 배치 작업 실패 시 로그 기록
     * - 다음날 재실행으로 복구 가능
     *
     * 모니터링:
     * - 집계된 상품 수 로그 출력
     * - 실행 시간 측정
     */
    @Scheduled(cron = "0 0 1 * * *")
    public void aggregateDailyStatistics() {
        long startTime = System.currentTimeMillis();
        log.info("==============================================");
        log.info("[스케줄러] 일일 상품 통계 집계 배치 시작");
        log.info("==============================================");

        try {
            // 전일 날짜 계산 (D-1)
            LocalDate targetDate = LocalDate.now().minusDays(1);

            // 통계 집계 실행
            int aggregatedCount = productStatisticsService.aggregateDailyStatistics(targetDate);

            long elapsedTime = System.currentTimeMillis() - startTime;
            log.info("==============================================");
            log.info("[스케줄러] 일일 상품 통계 집계 배치 완료");
            log.info("[스케줄러] - 대상 날짜: {}", targetDate);
            log.info("[스케줄러] - 집계된 상품 수: {}", aggregatedCount);
            log.info("[스케줄러] - 실행 시간: {}ms", elapsedTime);
            log.info("==============================================");

        } catch (Exception e) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            log.error("==============================================");
            log.error("[스케줄러] 일일 상품 통계 집계 배치 실패");
            log.error("[스케줄러] - 실행 시간: {}ms", elapsedTime);
            log.error("[스케줄러] - 오류 메시지: {}", e.getMessage(), e);
            log.error("==============================================");

            // 예외를 다시 던지지 않고 로그만 기록
            // 다음날 재실행으로 복구 가능
        }
    }

    /**
     * 수동 통계 집계 (테스트/운영용)
     *
     * 용도:
     * - 특정 날짜의 통계 재집계
     * - 배치 작업 실패 시 수동 복구
     * - 테스트 환경에서 통계 데이터 생성
     *
     * 사용 예:
     * - REST API를 통해 호출
     * - 관리자 페이지에서 실행
     *
     * @param targetDate 집계 대상 날짜
     * @return 집계된 상품 수
     */
    public int aggregateManually(LocalDate targetDate) {
        log.info("[수동 집계] 통계 집계 시작 - 대상 날짜: {}", targetDate);

        try {
            int aggregatedCount = productStatisticsService.aggregateDailyStatistics(targetDate);

            log.info("[수동 집계] 통계 집계 완료 - 대상 날짜: {}, 집계된 상품 수: {}",
                    targetDate, aggregatedCount);

            return aggregatedCount;

        } catch (Exception e) {
            log.error("[수동 집계] 통계 집계 실패 - 대상 날짜: {}, 오류: {}",
                    targetDate, e.getMessage(), e);
            throw e;
        }
    }
}
