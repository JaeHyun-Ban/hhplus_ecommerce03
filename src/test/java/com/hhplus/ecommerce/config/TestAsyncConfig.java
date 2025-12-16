package com.hhplus.ecommerce.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 테스트 환경용 비동기 설정
 *
 * 테스트에서는 비동기 작업을 동기로 실행하여 테스트의 일관성을 보장합니다.
 * SyncTaskExecutor를 사용하면 @Async 메서드가 호출 스레드에서 직접 실행됩니다.
 *
 * 장점:
 * - 테스트 코드가 비동기 작업 완료를 기다릴 필요 없음
 * - Thread.sleep() 같은 대기 로직 불필요
 * - 테스트 실행 시간 단축
 * - 테스트 결과의 일관성 보장
 */
@Slf4j
@TestConfiguration
public class TestAsyncConfig {

    /**
     * 테스트용 동기 실행기
     *
     * @Primary를 사용하여 프로덕션의 couponEventExecutor보다 우선순위를 높임
     *
     * @return SyncTaskExecutor - 비동기 작업을 동기로 실행
     */
    @Bean(name = "couponEventExecutor")
    @Primary
    public Executor couponEventExecutor() {
        log.info("테스트 환경: 비동기 작업을 동기로 실행하도록 설정");
        return new SyncTaskExecutor();
    }

    /**
     * 기본 비동기 실행기도 동기로 설정
     */
    @Bean
    @Primary
    public Executor taskExecutor() {
        log.info("테스트 환경: 기본 비동기 실행기를 동기로 설정");
        return new SyncTaskExecutor();
    }
}
