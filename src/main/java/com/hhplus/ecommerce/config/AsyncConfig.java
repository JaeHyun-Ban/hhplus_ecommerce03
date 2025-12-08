package com.hhplus.ecommerce.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 비동기 처리 설정
 *
 * 쿠폰 발급 이벤트를 비동기로 처리하기 위한 Thread Pool 설정
 *
 * Thread Pool 전략:
 * - Core Pool Size: 10 (기본 스레드 수)
 * - Max Pool Size: 20 (최대 스레드 수)
 * - Queue Capacity: 100 (대기 큐 크기)
 * - Keep Alive: 60초 (유휴 스레드 생존 시간)
 *
 * Rejection Policy: CallerRunsPolicy
 * - 큐가 가득 차면 호출한 스레드에서 직접 실행 (요청 손실 방지)
 *
 * 성능 고려사항:
 * - 선착순 100개 쿠폰에 120명 동시 요청 시
 * - 100개 이벤트가 큐에 적재
 * - 10개 스레드가 병렬로 DB 저장 처리
 * - Deadlock 발생 시 @Retryable로 자동 재시도
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * 쿠폰 이벤트 처리용 Thread Pool
     *
     * @return ThreadPoolTaskExecutor
     */
    @Bean(name = "couponEventExecutor")
    public Executor couponEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Thread Pool 크기 설정
        executor.setCorePoolSize(10);           // 기본 스레드 수
        executor.setMaxPoolSize(20);            // 최대 스레드 수
        executor.setQueueCapacity(100);         // 대기 큐 크기
        executor.setKeepAliveSeconds(60);       // 유휴 스레드 생존 시간

        // Thread 이름 접두사
        executor.setThreadNamePrefix("coupon-event-");

        // Rejection Policy: 큐 가득 차면 호출한 스레드에서 실행
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Graceful Shutdown: 애플리케이션 종료 시 대기 중인 작업 완료
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        log.info("쿠폰 이벤트 처리용 Thread Pool 초기화 완료 - core: {}, max: {}, queue: {}",
                 executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }

    /**
     * 기본 비동기 실행기
     */
    @Override
    public Executor getAsyncExecutor() {
        return couponEventExecutor();
    }

    /**
     * 비동기 작업 예외 핸들러
     *
     * @Async 메서드에서 발생한 예외를 로깅
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            log.error("비동기 작업 예외 발생 - method: {}, params: {}, error: {}",
                      method.getName(), params, throwable.getMessage(), throwable);
        };
    }
}
