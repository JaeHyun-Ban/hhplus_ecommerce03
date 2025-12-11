package com.hhplus.ecommerce.common.constants;

/**
 * 캐시 관련 상수 모음
 *
 * 책임:
 * - 캐시 이름 통합 관리
 * - 캐시 TTL 설정 통합 관리
 *
 * 목적:
 * - 매직 문자열 제거
 * - 캐시 이름 일관성 보장
 * - TTL 설정 중앙 관리
 *
 * 캐시 전략:
 * - 실시간 변경 데이터는 캐싱하지 않음 (balance, stock, issuedQuantity)
 * - TTL 설정으로 stale data 방지
 * - @CacheEvict로 명시적 캐시 무효화
 */
public final class CacheConstants {

    private CacheConstants() {
        throw new AssertionError("상수 클래스는 인스턴스화할 수 없습니다.");
    }

    // ========== 캐시 이름 ==========

    /**
     * 캐시 이름
     * - Spring Cache Abstraction에서 사용하는 캐시 이름
     * - @Cacheable, @CacheEvict 어노테이션에 사용
     */
    public static final class Names {
        private Names() {}

        /**
         * 상품 기본 정보 캐시
         * - Key: product-info::{productId}
         * - Value: Product 엔티티
         * - TTL: 1시간
         */
        public static final String PRODUCT_INFO = "product-info";

        /**
         * 인기 상품 목록 캐시
         * - Key: product-popular::all
         * - Value: List<Product>
         * - TTL: 5분
         */
        public static final String PRODUCT_POPULAR = "product-popular";

        /**
         * 쿠폰 메타데이터 캐시
         * - Key: coupon-info::{couponId}
         * - Value: Coupon 엔티티
         * - TTL: 30분
         */
        public static final String COUPON_INFO = "coupon-info";

        /**
         * 사용자 프로필 캐시
         * - Key: user-profile::{userId}
         * - Value: User 엔티티
         * - TTL: 1시간
         */
        public static final String USER_PROFILE = "user-profile";
    }

    // ========== 캐시 Key (Redis 직접 접근용) ==========

    /**
     * Redis에 직접 접근할 때 사용하는 캐시 키
     * - Spring Cache 외에 RedisTemplate으로 직접 접근하는 경우
     */
    public static final class Keys {
        private Keys() {}

        /**
         * 인기 상품 TOP 5 캐시 키
         * - RedisTemplate으로 직접 관리
         * - Key: cache:popular:products:top5
         * - TTL: 10분
         */
        public static final String POPULAR_PRODUCTS_TOP5 = "cache:popular:products:top5";
    }

    // ========== TTL 설정 (시간) ==========

    /**
     * 캐시 TTL 설정 - 시간(Hour) 단위
     */
    public static final class TtlHours {
        private TtlHours() {}

        /**
         * 상품 정보 캐시 TTL: 1시간
         * - 변경 빈도가 낮음
         */
        public static final long PRODUCT_INFO = 1L;

        /**
         * 사용자 프로필 캐시 TTL: 1시간
         * - 변경 빈도가 낮음
         */
        public static final long USER_PROFILE = 1L;

        /**
         * 상품 정보 Redis 캐시 TTL: 24시간
         * - ProductRedisRepository에서 직접 관리
         */
        public static final long PRODUCT_INFO_REDIS = 24L;
    }

    // ========== TTL 설정 (분) ==========

    /**
     * 캐시 TTL 설정 - 분(Minute) 단위
     */
    public static final class TtlMinutes {
        private TtlMinutes() {}

        /**
         * 기본 캐시 TTL: 10분
         */
        public static final long DEFAULT = 10L;

        /**
         * 인기 상품 목록 캐시 TTL: 5분
         * - 주기적 갱신 필요
         */
        public static final long PRODUCT_POPULAR = 5L;

        /**
         * 쿠폰 메타데이터 캐시 TTL: 30분
         * - 발급 기간 중 변경 없음
         */
        public static final long COUPON_INFO = 30L;

        /**
         * 인기 상품 TOP 5 캐시 TTL: 10분
         * - ProductService에서 직접 관리
         */
        public static final long POPULAR_PRODUCTS_TOP5 = 10L;
    }
}
