package com.hhplus.ecommerce.common.domain.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 이벤트 페이로드 인터페이스
 *
 * Common Layer - 이벤트 페이로드
 *
 * 책임:
 * - 모든 이벤트 페이로드의 마커 인터페이스
 * - JSON 직렬화/역직렬화를 위한 타입 정보 제공
 *
 * Jackson Polymorphic Type Handling:
 * - @JsonTypeInfo: 타입 정보를 JSON에 포함
 * - @JsonSubTypes: 구체적인 타입 매핑
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "@type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = CouponUsagePayload.class, name = "CouponUsagePayload"),
    @JsonSubTypes.Type(value = PopularProductAggregationPayload.class, name = "PopularProductAggregationPayload")
})
public interface EventPayload {
}
