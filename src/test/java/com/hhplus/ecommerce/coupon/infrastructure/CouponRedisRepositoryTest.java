package com.hhplus.ecommerce.coupon.infrastructure;

import com.hhplus.ecommerce.config.TestContainersConfig;
import com.hhplus.ecommerce.coupon.infrastructure.persistence.CouponRedisRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

/**
 * CouponRedisRepository Sorted Set 단위 테스트
 */
@Slf4j
@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("CouponRedisRepository Sorted Set 단위 테스트")
class CouponRedisRepositoryTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    static {
        redis.start();
        System.setProperty("spring.data.redis.host", redis.getHost());
        System.setProperty("spring.data.redis.port", redis.getMappedPort(6379).toString());
    }

    @Autowired
    private CouponRedisRepository couponRedisRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        // Redis 초기화
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("성공: 쿠폰 발급 - Sorted Set + Lua Script")
    void issue_Success() {
        // Given
        Long couponId = 1L;
        Long userId = 100L;
        Integer totalQuantity = 10;
        Integer maxIssuePerUser = 2;

        // When
        CouponRedisRepository.IssueResult result = couponRedisRepository.issue(
            couponId, userId, totalQuantity, maxIssuePerUser
        );

        // Then
        log.info("발급 결과 - success: {}, message: {}, count: {}, rank: {}",
                result.isSuccess(), result.getMessage(), result.getIssuedCount(), result.getRank());
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).as("발급 성공 여부").isTrue();
        assertThat(result.getMessage()).isEqualTo("SUCCESS");
        assertThat(result.getIssuedCount()).isEqualTo(1L);
        assertThat(result.getRank()).isEqualTo(1L);
    }

    @Test
    @DisplayName("성공: 순위 확인")
    void issue_RankCheck() {
        // Given
        Long couponId = 1L;
        Integer totalQuantity = 100;
        Integer maxIssuePerUser = 1;

        // When - 3명 발급
        CouponRedisRepository.IssueResult result1 = couponRedisRepository.issue(couponId, 1L, totalQuantity, maxIssuePerUser);
        CouponRedisRepository.IssueResult result2 = couponRedisRepository.issue(couponId, 2L, totalQuantity, maxIssuePerUser);
        CouponRedisRepository.IssueResult result3 = couponRedisRepository.issue(couponId, 3L, totalQuantity, maxIssuePerUser);

        // Then
        log.info("1등: {}", result1);
        log.info("2등: {}", result2);
        log.info("3등: {}", result3);

        assertThat(result1.getRank()).isEqualTo(1L);
        assertThat(result2.getRank()).isEqualTo(2L);
        assertThat(result3.getRank()).isEqualTo(3L);

        // 발급 수량 확인
        Long count = couponRedisRepository.getIssuedCount(couponId);
        assertThat(count).isEqualTo(3L);
    }

    @Test
    @DisplayName("실패: 수량 소진")
    void issue_Fail_SoldOut() {
        // Given
        Long couponId = 1L;
        Integer totalQuantity = 2;
        Integer maxIssuePerUser = 1;

        // 2명 발급 (수량 소진)
        couponRedisRepository.issue(couponId, 1L, totalQuantity, maxIssuePerUser);
        couponRedisRepository.issue(couponId, 2L, totalQuantity, maxIssuePerUser);

        // When - 3번째 시도
        CouponRedisRepository.IssueResult result = couponRedisRepository.issue(
            couponId, 3L, totalQuantity, maxIssuePerUser
        );

        // Then
        log.info("발급 결과: {}", result);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("SOLD_OUT");
    }

    @Test
    @DisplayName("실패: 중복 발급 - 사용자별 발급 제한 초과")
    void issue_Fail_AlreadyIssued() {
        // Given
        Long couponId = 1L;
        Long userId = 1L;
        Integer totalQuantity = 10;
        Integer maxIssuePerUser = 1;

        // 1번 발급
        couponRedisRepository.issue(couponId, userId, totalQuantity, maxIssuePerUser);

        // When - 같은 사용자가 다시 발급 시도 (maxIssuePerUser = 1 초과)
        CouponRedisRepository.IssueResult result = couponRedisRepository.issue(
            couponId, userId, totalQuantity, maxIssuePerUser
        );

        // Then - 사용자별 발급 제한 초과로 실패
        log.info("발급 결과: {}", result);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("EXCEED_USER_LIMIT");
    }

    @Test
    @DisplayName("실패: 사용자별 발급 제한 초과")
    void issue_Fail_ExceedUserLimit() {
        // Given
        Long couponId = 1L;
        Long userId = 1L;
        Integer totalQuantity = 100;
        Integer maxIssuePerUser = 2;

        // 2번 발급
        couponRedisRepository.issue(couponId, userId, totalQuantity, maxIssuePerUser);
        couponRedisRepository.issue(couponId, userId, totalQuantity, maxIssuePerUser);

        // When - 3번째 시도 (초과)
        CouponRedisRepository.IssueResult result = couponRedisRepository.issue(
            couponId, userId, totalQuantity, maxIssuePerUser
        );

        // Then
        log.info("발급 결과: {}", result);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("EXCEED_USER_LIMIT");
    }
}
