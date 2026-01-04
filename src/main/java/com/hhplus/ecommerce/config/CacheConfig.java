package com.hhplus.ecommerce.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 캐시 설정
 *
 * 캐시 전략:
 * - product:info: 상품 기본 정보 (1시간 TTL)
 * - product:popular: 인기 상품 목록 (5분 TTL)
 * - coupon:info: 쿠폰 메타데이터 (30분 TTL)
 * - user:profile: 사용자 프로필 (1시간 TTL)
 *
 * 주의사항:
 * - 실시간 변경 데이터는 캐싱하지 않음 (balance, stock, issuedQuantity)
 * - TTL 설정으로 stale data 방지
 * - @CacheEvict로 명시적 캐시 무효화
 */
@Slf4j
@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig {

    private final ObjectMapper cacheObjectMapper;

    // Cache TTL (Hours)
    private static final long CACHE_TTL_PRODUCT_INFO_HOURS = 1L;
    private static final long CACHE_TTL_USER_PROFILE_HOURS = 1L;

    // Cache TTL (Minutes)
    private static final long CACHE_TTL_DEFAULT_MINUTES = 10L;
    private static final long CACHE_TTL_PRODUCT_POPULAR_MINUTES = 5L;
    private static final long CACHE_TTL_COUPON_INFO_MINUTES = 30L;

    /**
     * Redis 캐시 매니저 설정
     * - ObjectMapperConfig에서 설정된 cacheObjectMapper 빈을 주입받아 사용
     * - 무거운 ObjectMapper 객체를 재사용하여 성능 최적화
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 기본 캐시 설정
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(CACHE_TTL_DEFAULT_MINUTES))  // 기본 TTL: 10분
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()
                )
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer(cacheObjectMapper)
                )
            )
            .disableCachingNullValues();  // null 값은 캐싱하지 않음

        // 캐시별 개별 TTL 설정
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // 상품 정보: 1시간 캐싱 (변경 빈도 낮음)
        cacheConfigurations.put(CacheNames.PRODUCT_INFO,
            defaultConfig.entryTtl(Duration.ofHours(CACHE_TTL_PRODUCT_INFO_HOURS)));

        // 인기 상품 목록: 5분 캐싱 (주기적 갱신)
        cacheConfigurations.put(CacheNames.PRODUCT_POPULAR,
            defaultConfig.entryTtl(Duration.ofMinutes(CACHE_TTL_PRODUCT_POPULAR_MINUTES)));

        // 쿠폰 메타데이터: 30분 캐싱 (발급 기간 중 변경 없음)
        cacheConfigurations.put(CacheNames.COUPON_INFO,
            defaultConfig.entryTtl(Duration.ofMinutes(CACHE_TTL_COUPON_INFO_MINUTES)));

        // 사용자 프로필: 1시간 캐싱 (변경 빈도 낮음)
        cacheConfigurations.put(CacheNames.USER_PROFILE,
            defaultConfig.entryTtl(Duration.ofHours(CACHE_TTL_USER_PROFILE_HOURS)));

        log.info("Redis 캐시 매니저 초기화 완료 - 캐시 종류: {}", cacheConfigurations.size());

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build();
    }
}
