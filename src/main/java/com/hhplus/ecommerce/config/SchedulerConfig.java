package com.hhplus.ecommerce.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄러 설정
 *
 * 배치 작업:
 * - 일일 상품 통계 집계
 * - 쿠폰 만료 처리
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
}
