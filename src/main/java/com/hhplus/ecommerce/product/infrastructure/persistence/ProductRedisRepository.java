package com.hhplus.ecommerce.product.infrastructure.persistence;

import com.hhplus.ecommerce.product.domain.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 상품 Redis Repository
 *
 * Infrastructure Layer - Redis 데이터 접근 계층
 *
 * 책임:
 * - 실시간 인기상품 집계 및 조회
 * - 상품 정보 캐싱 (Hash 자료구조)
 * - 인기도 순위 관리 (Sorted Set 자료구조)
 *
 * Redis 자료구조:
 * 1. Sorted Set: 인기상품 순위
 *    - Key: popular:products
 *    - Member: productId
 *    - Score: 판매 수량 (주문 수량 누적)
 *    - 연산: ZINCRBY (스코어 증가), ZREVRANGE (높은 순위부터 조회)
 *
 * 2. Hash: 상품 정보 캐시
 *    - Key: info:product:{productId}
 *    - Fields: id, name, description, price, stock, categoryId
 *    - 연산: HSET (저장), HGETALL (조회), DEL (삭제)
 *
 * Use Cases:
 * - 결제 완료 시 인기상품 집계
 * - 인기상품 TOP N 조회
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ProductRedisRepository {

    private final RedisTemplate<String, Object> redisTemplate;

    // Redis Key Prefix
    private static final String POPULAR_PRODUCTS_KEY = "popular:products";
    private static final String PRODUCT_INFO_PREFIX = "info:product:";

    // TTL 설정
    private static final long PRODUCT_INFO_TTL_HOURS = 24; // 상품 정보 캐시: 24시간

    /**
     * 인기상품 스코어 증가 (주문 시 호출)
     *
     * 주문된 상품의 판매 수량을 인기도 스코어에 반영
     * ZINCRBY 명령어를 사용하여 원자적으로 스코어 증가
     *
     * @param productId 상품 ID
     * @param quantity 주문 수량
     */
    public void incrementPopularityScore(Long productId, Integer quantity) {
        try {
            // Sorted Set에 스코어 증가 (원자적 연산)
            Double newScore = redisTemplate.opsForZSet()
                .incrementScore(POPULAR_PRODUCTS_KEY, productId.toString(), quantity.doubleValue());

            log.debug("인기상품 스코어 증가 - productId: {}, quantity: {}, newScore: {}",
                     productId, quantity, newScore);

        } catch (Exception e) {
            log.error("인기상품 스코어 증가 실패 - productId: {}, quantity: {}", productId, quantity, e);
            // Redis 장애 시에도 주문 프로세스는 계속 진행 (인기상품 집계는 부가 기능)
        }
    }

    /**
     * 상품 정보 캐시 저장
     *
     * Hash 자료구조에 상품 정보를 저장하여 DB 조회 최소화
     *
     * @param product 상품 엔티티
     */
    public void cacheProductInfo(Product product) {
        String key = PRODUCT_INFO_PREFIX + product.getId();

        try {
            Map<String, String> productInfo = new HashMap<>();
            productInfo.put("id", product.getId().toString());
            productInfo.put("name", product.getName());
            productInfo.put("description", product.getDescription() != null ? product.getDescription() : "");
            productInfo.put("price", product.getPrice().toString());
            productInfo.put("stock", product.getStock().toString());
            productInfo.put("categoryId", product.getCategory() != null ? product.getCategory().getId().toString() : "");
            productInfo.put("status", product.getStatus().toString());

            // Hash에 상품 정보 저장
            redisTemplate.opsForHash().putAll(key, productInfo);

            // TTL 설정
            redisTemplate.expire(key, PRODUCT_INFO_TTL_HOURS, TimeUnit.HOURS);

            log.debug("상품 정보 캐시 저장 - productId: {}", product.getId());

        } catch (Exception e) {
            log.error("상품 정보 캐시 저장 실패 - productId: {}", product.getId(), e);
        }
    }

    /**
     * 상품 정보 캐시 조회
     *
     * @param productId 상품 ID
     * @return 상품 정보 Map (캐시 없으면 null)
     */
    public Map<String, String> getCachedProductInfo(Long productId) {
        String key = PRODUCT_INFO_PREFIX + productId;

        try {
            Map<Object, Object> rawMap = redisTemplate.opsForHash().entries(key);

            if (rawMap.isEmpty()) {
                return null;
            }

            // Object를 String으로 변환
            Map<String, String> productInfo = new HashMap<>();
            rawMap.forEach((k, v) -> productInfo.put(k.toString(), v.toString()));

            return productInfo;

        } catch (Exception e) {
            log.error("상품 정보 캐시 조회 실패 - productId: {}", productId, e);
            return null;
        }
    }

    /**
     * 인기상품 TOP N 조회 (ID만)
     *
     * Sorted Set에서 스코어가 높은 순으로 상위 N개 상품 ID 조회
     *
     * @param topN 조회할 상위 개수
     * @return 상품 ID 리스트 (인기도 순)
     */
    public List<Long> getTopPopularProductIds(int topN) {
        try {
            // Sorted Set에서 높은 스코어 순으로 조회 (ZREVRANGE)
            Set<Object> productIds = redisTemplate.opsForZSet()
                .reverseRange(POPULAR_PRODUCTS_KEY, 0, topN - 1);

            if (productIds == null || productIds.isEmpty()) {
                log.debug("인기상품 데이터 없음");
                return Collections.emptyList();
            }

            return productIds.stream()
                .map(id -> Long.parseLong(id.toString()))
                .toList();

        } catch (Exception e) {
            log.error("인기상품 TOP {} 조회 실패", topN, e);
            return Collections.emptyList();
        }
    }

    /**
     * 인기상품 TOP N 조회 (스코어 포함)
     *
     * @param topN 조회할 상위 개수
     * @return 상품 ID와 스코어 맵
     */
    public List<PopularProduct> getTopPopularProducts(int topN) {
        try {
            // Sorted Set에서 높은 스코어 순으로 조회 (WITH SCORES)
            Set<ZSetOperations.TypedTuple<Object>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(POPULAR_PRODUCTS_KEY, 0, topN - 1);

            if (tuples == null || tuples.isEmpty()) {
                log.debug("인기상품 데이터 없음");
                return Collections.emptyList();
            }

            return tuples.stream()
                .map(tuple -> {
                    Long productId = Long.parseLong(tuple.getValue().toString());
                    Long salesCount = tuple.getScore() != null ? tuple.getScore().longValue() : 0L;
                    return new PopularProduct(productId, salesCount);
                })
                .toList();

        } catch (Exception e) {
            log.error("인기상품 TOP {} 조회 실패 (스코어 포함)", topN, e);
            return Collections.emptyList();
        }
    }

    /**
     * 특정 상품의 인기도 순위 조회
     *
     * @param productId 상품 ID
     * @return 순위 (1부터 시작, 없으면 null)
     */
    public Long getProductRank(Long productId) {
        try {
            // Sorted Set에서 순위 조회 (ZREVRANK - 높은 스코어부터 순위 매김)
            Long rank = redisTemplate.opsForZSet()
                .reverseRank(POPULAR_PRODUCTS_KEY, productId.toString());

            // rank는 0부터 시작하므로 +1
            return rank != null ? rank + 1 : null;

        } catch (Exception e) {
            log.error("상품 순위 조회 실패 - productId: {}", productId, e);
            return null;
        }
    }

    /**
     * 특정 상품의 인기도 스코어 조회
     *
     * @param productId 상품 ID
     * @return 스코어 (판매 수량 누적값, 없으면 0)
     */
    public Long getProductScore(Long productId) {
        try {
            Double score = redisTemplate.opsForZSet()
                .score(POPULAR_PRODUCTS_KEY, productId.toString());

            return score != null ? score.longValue() : 0L;

        } catch (Exception e) {
            log.error("상품 스코어 조회 실패 - productId: {}", productId, e);
            return 0L;
        }
    }

    /**
     * 인기상품 데이터 초기화
     *
     * Use Case:
     * - 테스트 환경에서 데이터 초기화
     * - 주기적인 순위 리셋 (예: 월별 초기화)
     */
    public void resetPopularProducts() {
        try {
            redisTemplate.delete(POPULAR_PRODUCTS_KEY);
            log.info("인기상품 데이터 초기화 완료");

        } catch (Exception e) {
            log.error("인기상품 데이터 초기화 실패", e);
        }
    }

    /**
     * 상품 정보 캐시 삭제
     *
     * Use Case:
     * - 상품 정보 변경 시
     * - 상품 삭제 시
     *
     * @param productId 상품 ID
     */
    public void evictProductCache(Long productId) {
        String key = PRODUCT_INFO_PREFIX + productId;

        try {
            redisTemplate.delete(key);
            log.debug("상품 정보 캐시 삭제 - productId: {}", productId);

        } catch (Exception e) {
            log.error("상품 정보 캐시 삭제 실패 - productId: {}", productId, e);
        }
    }

    /**
     * 인기상품 통계 조회
     *
     * @return 통계 정보
     */
    public PopularProductStats getStats() {
        try {
            Long totalProducts = redisTemplate.opsForZSet().size(POPULAR_PRODUCTS_KEY);

            // 전체 판매 수량 합계
            Set<ZSetOperations.TypedTuple<Object>> all = redisTemplate.opsForZSet()
                .reverseRangeWithScores(POPULAR_PRODUCTS_KEY, 0, -1);

            long totalSales = 0L;
            if (all != null) {
                totalSales = all.stream()
                    .mapToLong(tuple -> tuple.getScore() != null ? tuple.getScore().longValue() : 0L)
                    .sum();
            }

            return PopularProductStats.builder()
                .totalProducts(totalProducts != null ? totalProducts : 0L)
                .totalSales(totalSales)
                .build();

        } catch (Exception e) {
            log.error("인기상품 통계 조회 실패", e);
            return PopularProductStats.builder()
                .totalProducts(0L)
                .totalSales(0L)
                .build();
        }
    }

    /**
     * 인기상품 DTO (상품 ID + 판매 수량)
     */
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class PopularProduct {
        private Long productId;
        private Long salesCount;
    }

    /**
     * 인기상품 통계 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class PopularProductStats {
        private Long totalProducts;  // 인기상품 수
        private Long totalSales;      // 전체 판매 수량
    }
}
