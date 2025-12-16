package com.hhplus.ecommerce.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.hibernate5.jakarta.Hibernate5JakartaModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    // Cache Names
    private static final String CACHE_NAME_PRODUCT_INFO = "product-info";
    private static final String CACHE_NAME_PRODUCT_POPULAR = "product-popular";
    private static final String CACHE_NAME_COUPON_INFO = "coupon-info";
    private static final String CACHE_NAME_USER_PROFILE = "user-profile";

    // Cache TTL (Hours)
    private static final long CACHE_TTL_PRODUCT_INFO_HOURS = 1L;
    private static final long CACHE_TTL_USER_PROFILE_HOURS = 1L;

    // Cache TTL (Minutes)
    private static final long CACHE_TTL_DEFAULT_MINUTES = 10L;
    private static final long CACHE_TTL_PRODUCT_POPULAR_MINUTES = 5L;
    private static final long CACHE_TTL_COUPON_INFO_MINUTES = 30L;

    /**
     * Redis 캐시 매니저 설정
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // ObjectMapper 설정
        ObjectMapper objectMapper = new ObjectMapper();

        // LocalDateTime 직렬화 지원
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Hibernate5 lazy loading 지원
        Hibernate5JakartaModule hibernate5Module = new Hibernate5JakartaModule();
        hibernate5Module.configure(Hibernate5JakartaModule.Feature.FORCE_LAZY_LOADING, false);
        objectMapper.registerModule(hibernate5Module);

        // 알 수 없는 속성 무시 (isAvailable() 같은 computed property 처리)
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 타입 정보 포함 (LinkedHashMap 변환 방지)
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
            .allowIfBaseType(Object.class)
            .build();
        objectMapper.activateDefaultTyping(
            typeValidator,
            ObjectMapper.DefaultTyping.NON_FINAL
        );

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
                    new GenericJackson2JsonRedisSerializer(objectMapper)
                )
            )
            .disableCachingNullValues();  // null 값은 캐싱하지 않음

        // 캐시별 개별 TTL 설정
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // 상품 정보: 1시간 캐싱 (변경 빈도 낮음)
        cacheConfigurations.put(CACHE_NAME_PRODUCT_INFO,
            defaultConfig.entryTtl(Duration.ofHours(CACHE_TTL_PRODUCT_INFO_HOURS)));

        // 인기 상품 목록: 5분 캐싱 (주기적 갱신)
        cacheConfigurations.put(CACHE_NAME_PRODUCT_POPULAR,
            defaultConfig.entryTtl(Duration.ofMinutes(CACHE_TTL_PRODUCT_POPULAR_MINUTES)));

        // 쿠폰 메타데이터: 30분 캐싱 (발급 기간 중 변경 없음)
        cacheConfigurations.put(CACHE_NAME_COUPON_INFO,
            defaultConfig.entryTtl(Duration.ofMinutes(CACHE_TTL_COUPON_INFO_MINUTES)));

        // 사용자 프로필: 1시간 캐싱 (변경 빈도 낮음)
        cacheConfigurations.put(CACHE_NAME_USER_PROFILE,
            defaultConfig.entryTtl(Duration.ofHours(CACHE_TTL_USER_PROFILE_HOURS)));

        log.info("Redis 캐시 매니저 초기화 완료 - 캐시 종류: {}", cacheConfigurations.size());

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build();
    }
}
