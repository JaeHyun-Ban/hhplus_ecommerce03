package com.hhplus.ecommerce.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 설정
 *
 * Redis 분산락을 위한 Redisson 클라이언트 설정
 *
 * 기능:
 * - RLock (재진입 가능한 분산락)
 * - Fair Lock (선입선출 방식 락)
 * - Watchdog (자동 락 갱신)
 * - Pub/Sub (자동 처리)
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    /**
     * RedissonClient Bean 생성
     *
     * 싱글 서버 모드 설정:
     * - address: redis://host:port
     * - connectionPoolSize: 연결 풀 크기 (기본 64)
     * - connectionMinimumIdleSize: 최소 유휴 연결 (기본 32)
     * - timeout: 명령 타임아웃 (기본 3초)
     * - retryAttempts: 재시도 횟수 (기본 3회)
     * - retryInterval: 재시도 간격 (기본 1.5초)
     *
     * @return RedissonClient 인스턴스
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        config.useSingleServer()
              .setAddress("redis://" + redisHost + ":" + redisPort)
              .setConnectionPoolSize(50)           // 연결 풀 크기
              .setConnectionMinimumIdleSize(10)    // 최소 유휴 연결
              .setTimeout(3000)                    // 3초 타임아웃
              .setRetryAttempts(3)                 // 3회 재시도
              .setRetryInterval(1500)              // 1.5초 간격
              .setDatabase(0);                     // DB 0 사용

        return Redisson.create(config);
    }
}
