package com.hhplus.ecommerce.common.constants;

/**
 * Redis 관련 상수 모음
 *
 * 책임:
 * - Redis Key Prefix 통합 관리
 * - TTL 설정 값 통합 관리
 * - Redis 관련 설정 값 통합 관리
 *
 * 목적:
 * - 매직 넘버/문자열 제거
 * - 상수 중앙 관리로 유지보수성 향상
 * - Key Naming 일관성 보장
 */
public final class RedisConstants {

    private RedisConstants() {
        throw new AssertionError("상수 클래스는 인스턴스화할 수 없습니다.");
    }

    // ========== Redis Key Prefix ==========

    /**
     * 쿠폰 관련 Key Prefix
     */
    public static final class Coupon {
        private Coupon() {}

        /**
         * 쿠폰 발급 내역 (Sorted Set)
         * Key 형식: coupon:issued:{couponId}
         */
        public static final String ISSUED_PREFIX = "coupon:issued:";

        /**
         * 사용자별 쿠폰 발급 수량 (Hash)
         * Key 형식: coupon:user:count:{couponId}
         */
        public static final String USER_COUNT_PREFIX = "coupon:user:count:";
    }

    /**
     * 상품 관련 Key Prefix
     */
    public static final class Product {
        private Product() {}

        /**
         * 상품 정보 캐시 (캐시 매니저 사용)
         * Key 형식: product:info:{productId}
         */
        public static final String INFO_PREFIX = "product:info:";

        /**
         * 상품 정보 캐시 (Redis 직접 접근)
         * Key 형식: info:product:{productId}
         */
        public static final String INFO_REDIS_PREFIX = "info:product:";

        /**
         * 인기 상품 랭킹 (Sorted Set)
         * Key: popular:products
         * - ProductRedisRepository에서 사용
         * - Score: 판매 수량, Member: productId
         */
        public static final String POPULAR_RANKING = "popular:products";
    }

    /**
     * 사용자 관련 Key Prefix
     */
    public static final class User {
        private User() {}

        /**
         * 사용자 프로필 캐시
         * Key 형식: user:profile:{userId}
         */
        public static final String PROFILE_PREFIX = "user:profile:";
    }

    // ========== TTL 설정 (일 단위) ==========

    /**
     * TTL 설정 - 일(Day) 단위
     */
    public static final class TtlDays {
        private TtlDays() {}

        /**
         * 쿠폰 발급 데이터 TTL: 7일
         */
        public static final long COUPON_DATA = 7L;

        /**
         * 상품 정보 캐시 TTL: 1일
         */
        public static final long PRODUCT_INFO = 1L;

        /**
         * 사용자 프로필 캐시 TTL: 1일
         */
        public static final long USER_PROFILE = 1L;
    }

    // ========== TTL 설정 (시간 단위) ==========

    /**
     * TTL 설정 - 시간(Hour) 단위
     */
    public static final class TtlHours {
        private TtlHours() {}

        /**
         * 상품 정보 캐시 TTL: 1시간
         */
        public static final long PRODUCT_INFO = 1L;

        /**
         * 사용자 프로필 캐시 TTL: 1시간
         */
        public static final long USER_PROFILE = 1L;
    }

    // ========== TTL 설정 (분 단위) ==========

    /**
     * TTL 설정 - 분(Minute) 단위
     */
    public static final class TtlMinutes {
        private TtlMinutes() {}

        /**
         * 쿠폰 정보 캐시 TTL: 30분
         */
        public static final long COUPON_INFO = 30L;

        /**
         * 인기 상품 목록 캐시 TTL: 5분
         */
        public static final long PRODUCT_POPULAR = 5L;

        /**
         * 기본 캐시 TTL: 10분
         */
        public static final long DEFAULT_CACHE = 10L;
    }
}