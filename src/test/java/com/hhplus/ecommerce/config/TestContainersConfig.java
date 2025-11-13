package com.hhplus.ecommerce.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * TestContainers 설정 클래스
 *
 * 통합 테스트에서 실제 MySQL 컨테이너를 사용하기 위한 설정
 * Spring Boot 3.1+ 의 @ServiceConnection 기능을 활용하여
 * 자동으로 DataSource 설정을 구성합니다.
 */
@TestConfiguration(proxyBeanMethods = false)
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
}