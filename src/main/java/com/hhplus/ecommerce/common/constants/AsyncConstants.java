package com.hhplus.ecommerce.common.constants;

/**
 * 비동기 처리 관련 상수 모음
 *
 * 책임:
 * - Thread Pool 설정 값 통합 관리
 * - 비동기 실행기 설정 통합 관리
 *
 * 목적:
 * - 매직 넘버 제거
 * - 상수 중앙 관리로 유지보수성 향상
 * - Thread Pool 설정 일관성 보장
 */
public final class AsyncConstants {

    private AsyncConstants() {
        throw new AssertionError("상수 클래스는 인스턴스화할 수 없습니다.");
    }

    // ========== Thread Pool 설정 ==========

    /**
     * 쿠폰 이벤트 처리용 Thread Pool 설정
     */
    public static final class CouponEventExecutor {
        private CouponEventExecutor() {}

        /**
         * Bean 이름
         */
        public static final String BEAN_NAME = "couponEventExecutor";

        /**
         * 기본 스레드 수 (Core Pool Size)
         * - 항상 유지되는 최소 스레드 개수
         */
        public static final int CORE_POOL_SIZE = 10;

        /**
         * 최대 스레드 수 (Max Pool Size)
         * - 부하가 높을 때 생성 가능한 최대 스레드 개수
         */
        public static final int MAX_POOL_SIZE = 20;

        /**
         * 대기 큐 크기 (Queue Capacity)
         * - 스레드가 모두 사용 중일 때 대기하는 작업 큐 크기
         */
        public static final int QUEUE_CAPACITY = 100;

        /**
         * 유휴 스레드 생존 시간 (초)
         * - Core Pool Size를 초과한 스레드가 유휴 상태일 때 제거되기까지의 시간
         */
        public static final int KEEP_ALIVE_SECONDS = 60;

        /**
         * Graceful Shutdown 대기 시간 (초)
         * - 애플리케이션 종료 시 실행 중인 작업이 완료될 때까지 대기하는 시간
         */
        public static final int AWAIT_TERMINATION_SECONDS = 60;

        /**
         * 스레드 이름 접두사
         * - 로그 추적 및 디버깅에 활용
         */
        public static final String THREAD_NAME_PREFIX = "coupon-event-";
    }
}
