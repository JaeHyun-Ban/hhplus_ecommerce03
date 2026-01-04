package com.hhplus.ecommerce.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.hibernate5.jakarta.Hibernate5JakartaModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * ObjectMapper 설정 클래스
 *
 * 목적:
 * - ObjectMapper 빈을 중앙에서 관리
 * - Redis 캐시용 ObjectMapper 설정 제공
 * - 무거운 객체를 재사용하여 성능 최적화
 *
 * 설정 내용:
 * - JavaTimeModule: LocalDateTime 등 Java 8 시간 API 지원
 * - Hibernate5JakartaModule: JPA 엔티티의 Lazy Loading 지원
 * - PolymorphicTypeValidator: 타입 안전성 보장
 */
@Configuration
public class ObjectMapperConfig {

    /**
     * 기본 ObjectMapper 빈
     * - 일반적인 JSON 직렬화/역직렬화에 사용
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        // Java 8 시간 API 지원 (LocalDateTime, LocalDate 등)
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 알 수 없는 속성 무시 (유연한 역직렬화)
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        return objectMapper;
    }

    /**
     * Redis 캐시용 ObjectMapper 빈
     * - JPA 엔티티 캐싱에 특화된 설정
     * - Hibernate Lazy Loading 지원
     * - 타입 정보 포함하여 LinkedHashMap 변환 방지
     */
    @Bean("cacheObjectMapper")
    public ObjectMapper cacheObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        // Java 8 시간 API 지원
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Hibernate5 Lazy Loading 지원
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

        return objectMapper;
    }
}
