package com.hhplus.ecommerce.config;

/**
 * 캐시 이름 상수 관리 클래스
 *
 * 목적:
 * - 캐시 이름을 한 곳에서 중앙 관리
 * - 하드코딩된 문자열 사용 방지
 * - 오타 및 불일치 문제 해결
 * - 유지보수성 향상
 *
 * 사용:
 * - @Cacheable(value = CacheNames.PRODUCT_INFO)
 * - @CacheEvict(value = CacheNames.PRODUCT_INFO)
 * - cacheConfigurations.put(CacheNames.PRODUCT_INFO, ...)
 */
public final class CacheNames {

    private CacheNames() {
        throw new AssertionError("상수 클래스는 인스턴스화할 수 없습니다.");
    }

    /**
     * 상품 기본 정보 캐시
     * - TTL: 1시간
     * - 용도: 상품 상세 조회 (@Cacheable)
     */
    public static final String PRODUCT_INFO = "product-info";

    /**
     * 인기 상품 목록 캐시
     * - TTL: 5분
     * - 용도: 인기 상품 조회 (주기적 갱신)
     */
    public static final String PRODUCT_POPULAR = "product-popular";

    /**
     * 쿠폰 메타데이터 캐시
     * - TTL: 30분
     * - 용도: 쿠폰 정보 조회 (발급 기간 중 변경 없음)
     */
    public static final String COUPON_INFO = "coupon-info";

    /**
     * 사용자 프로필 캐시
     * - TTL: 1시간
     * - 용도: 사용자 정보 조회 (변경 빈도 낮음)
     */
    public static final String USER_PROFILE = "user-profile";
}