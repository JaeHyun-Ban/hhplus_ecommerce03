package com.hhplus.ecommerce.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Spring Retry 설정
 *
 * @Retryable 어노테이션을 사용하기 위한 설정
 *
 * 사용 예시:
 * - CouponService.issueCoupon(): 낙관적 락 충돌 시 재시도
 */
@Configuration
@EnableRetry
public class RetryConfig {
}
