package com.hhplus.ecommerce.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * TestContainers 설정 클래스
 *
 * 통합 테스트에서 실제 MySQL, Redis 컨테이너를 사용하기 위한 설정
 * Spring Boot 3.1+ 의 @ServiceConnection 기능을 활용하여
 * 자동으로 DataSource 설정을 구성합니다.
 *
 * TestAsyncConfig를 Import하여 테스트 환경에서는 비동기 작업이 동기로 실행되도록 설정
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(TestAsyncConfig.class)
public class TestContainersConfig {

    /**
     * MySQL 컨테이너 Bean 생성
     *
     * @ServiceConnection 어노테이션으로 자동 설정:
     * - spring.datasource.url
     * - spring.datasource.username
     * - spring.datasource.password
     */
    @Bean
    @ServiceConnection
    public MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true); // 컨테이너 재사용으로 테스트 속도 향상
    }

    /**
     * Redis 컨테이너 Bean 생성
     *
     * System property로 수동 설정:
     * - spring.data.redis.host
     * - spring.data.redis.port
     */
    @Bean
    public GenericContainer<?> redisContainer() {
        GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379)
                .withReuse(true); // 컨테이너 재사용으로 테스트 속도 향상

        redis.start();

        // Redisson과 RedisTemplate 모두 사용 가능하도록 설정
        System.setProperty("spring.data.redis.host", redis.getHost());
        System.setProperty("spring.data.redis.port", redis.getMappedPort(6379).toString());

        return redis;
    }
}