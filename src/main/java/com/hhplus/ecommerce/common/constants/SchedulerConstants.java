package com.hhplus.ecommerce.common.constants;

/**
 * 스케줄러 관련 상수 모음
 *
 * 책임:
 * - 배치 작업용 Lock Key 통합 관리
 * - Cron 표현식 통합 관리
 *
 * 목적:
 * - 매직 문자열 제거
 * - Lock Key 일관성 보장
 * - 스케줄 설정 중앙 관리
 */
public final class SchedulerConstants {

    private SchedulerConstants() {
        throw new AssertionError("상수 클래스는 인스턴스화할 수 없습니다.");
    }

    // ========== Lock Keys ==========

    /**
     * 배치 작업용 분산 락 키
     * - 스케줄러가 여러 인스턴스에서 실행될 때 중복 실행 방지
     */
    public static final class LockKeys {
        private LockKeys() {}

        /**
         * 상품 통계 일일 집계 배치 Lock
         * - Key: lock:batch:product-statistics:daily
         * - 매일 새벽 1시 실행되는 배치 작업
         */
        public static final String PRODUCT_STATISTICS_DAILY = "lock:batch:product-statistics:daily";

        /**
         * 인기 상품 갱신 Lock
         * - Key: lock:popular:products:refresh
         * - 인기 상품 목록 갱신 작업
         */
        public static final String POPULAR_PRODUCTS_REFRESH = "lock:popular:products:refresh";

        /**
         * 쿠폰 이벤트 재시도 Lock
         * - Key: lock:coupon:event:retry
         * - 실패한 쿠폰 이벤트 재시도 작업
         */
        public static final String COUPON_EVENT_RETRY = "lock:coupon:event:retry";
    }

    // ========== Cron 표현식 ==========

    /**
     * Cron 표현식
     * - @Scheduled 어노테이션에 사용
     * - 형식: 초 분 시 일 월 요일
     */
    public static final class Cron {
        private Cron() {}

        /**
         * 매일 새벽 1시 실행
         * - 상품 통계 일일 집계에 사용
         */
        public static final String DAILY_1AM = "0 0 1 * * *";

        /**
         * 매 5분마다 실행
         * - 인기 상품 갱신에 사용 (필요시)
         */
        public static final String EVERY_5_MINUTES = "0 */5 * * * *";

        /**
         * 매 10분마다 실행
         * - 캐시 갱신 작업에 사용 (필요시)
         */
        public static final String EVERY_10_MINUTES = "0 */10 * * * *";

        /**
         * 매분마다 실행
         * - 쿠폰 이벤트 재시도에 사용
         */
        public static final String EVERY_MINUTE = "0 * * * * *";
    }

    // ========== Timeout 설정 ==========

    /**
     * 배치 작업용 Lock Timeout 설정 - 초(Second) 단위
     */
    public static final class Timeout {
        private Timeout() {}

        /**
         * Lock 획득 대기 시간: 0초
         * - 배치 작업은 대기하지 않고 즉시 포기
         */
        public static final long WAIT_TIME_SECONDS = 0L;

        /**
         * Lock 자동 해제 시간: 300초 (5분)
         * - 배치 작업이 오래 걸릴 수 있으므로 5분으로 설정
         */
        public static final long LEASE_TIME_SECONDS = 300L;
    }
}
