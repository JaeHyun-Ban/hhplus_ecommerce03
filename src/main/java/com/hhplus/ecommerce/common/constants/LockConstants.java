package com.hhplus.ecommerce.common.constants;

/**
 * 분산 락(Distributed Lock) 관련 상수 모음
 *
 * 책임:
 * - Lock Key Prefix 통합 관리
 * - Lock Timeout 설정 값 통합 관리
 * - Lock 관련 설정 값 통합 관리
 *
 * 목적:
 * - 매직 넘버/문자열 제거
 * - 상수 중앙 관리로 유지보수성 향상
 * - Lock Key Naming 일관성 보장
 * - Timeout 값 통합 관리
 */
public final class LockConstants {

    private LockConstants() {
        throw new AssertionError("상수 클래스는 인스턴스화할 수 없습니다.");
    }

    // ========== Lock Key Prefix ==========

    /**
     * 잔액 관련 Lock Key Prefix
     */
    public static final class Balance {
        private Balance() {}

        /**
         * 사용자 잔액 Lock
         * Key 형식: lock:balance:user:{userId}
         */
        public static final String USER_LOCK_PREFIX = "lock:balance:user:";
    }

    /**
     * 주문 관련 Lock Key Prefix
     */
    public static final class Order {
        private Order() {}

        /**
         * 주문 처리 Lock
         * Key 형식: lock:order:{orderId}
         */
        public static final String LOCK_PREFIX = "lock:order:";
    }

    /**
     * 재고 관련 Lock Key Prefix
     */
    public static final class Stock {
        private Stock() {}

        /**
         * 상품 재고 Lock
         * Key 형식: lock:stock:product:{productId}
         */
        public static final String PRODUCT_LOCK_PREFIX = "lock:stock:product:";
    }

    // ========== Lock Timeout 설정 (초 단위) ==========

    /**
     * Lock Timeout 설정 - 초(Second) 단위
     */
    public static final class Timeout {
        private Timeout() {}

        /**
         * Lock 획득 대기 시간: 10초
         * - 락을 획득하기 위해 대기하는 최대 시간
         */
        public static final long WAIT_TIME_SECONDS = 10L;

        /**
         * Lock 자동 해제 시간: 10초
         * - 락을 획득한 후 자동으로 해제되는 시간
         * - 데드락 방지를 위한 안전 장치
         */
        public static final long LEASE_TIME_SECONDS = 10L;

        /**
         * Lock 획득 재시도 간격: 100ms
         * - 락 획득 실패 시 재시도하기 전 대기 시간
         */
        public static final long RETRY_INTERVAL_MILLIS = 100L;
    }
}