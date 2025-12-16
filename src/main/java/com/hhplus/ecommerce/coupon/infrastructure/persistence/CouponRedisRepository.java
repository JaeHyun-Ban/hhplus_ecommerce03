package com.hhplus.ecommerce.coupon.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 쿠폰 Redis Repository (Sorted Set 기반)
 *
 * Infrastructure Layer - Redis 데이터 접근 계층
 *
 * 책임:
 * - 선착순 쿠폰 발급을 위한 Redis 원자적 연산
 * - 발급 수량 실시간 관리
 * - 중복 발급 방지
 * - 발급 순서 타임스탬프 기록 (선착순 증명)
 * - DB 부하 최소화
 *
 * Redis 자료구조 (Sorted Set 기반):
 * 1. Sorted Set: 쿠폰 발급 내역 + 선착순 순서
 *    - Key: coupon:issued:{couponId}
 *    - Member: {userId}
 *    - Score: 발급 타임스탬프 (밀리초)
 *    - 연산: ZADD NX (중복 방지), ZCARD (발급 수량), ZRANK (선착순 순위)
 *    - 장점:
 *      * 발급 순서를 타임스탬프로 기록 (선착순 증명)
 *      * ZADD NX로 중복 발급 자동 방지
 *      * ZRANK로 발급 순위 조회 가능
 *      * ZCARD로 O(1) 시간복잡도로 발급 수량 조회
 *
 * 2. Hash: 사용자별 발급 수량 카운터
 *    - Key: coupon:user:count:{couponId}
 *    - Field: {userId}
 *    - Value: 사용자가 해당 쿠폰을 발급받은 횟수
 *    - 연산: HINCRBY (원자적 증가), HGET
 *    - 용도: 1인당 최대 발급 수량 제한 (maxIssuePerUser)
 *
 * 원자성 보장:
 * - Lua Script를 사용하여 모든 연산을 원자적으로 실행
 * - Redis Single Thread 특성으로 Race Condition 방지
 *
 * Use Cases:
 * - UC-017: 선착순 쿠폰 발급
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class CouponRedisRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    // Redis Constants
    private static final String COUPON_ISSUED_PREFIX = "coupon:issued:";
    private static final String COUPON_USER_COUNT_PREFIX = "coupon:user:count:";
    private static final long COUPON_DATA_TTL_DAYS = 7L;

    /**
     * Lua Script: 선착순 쿠폰 발급
     *
     * 원자적 실행 보장:
     * - Redis는 Single Thread로 Lua Script를 실행
     * - Script 실행 중 다른 명령어가 끼어들 수 없음
     *
     * 프로세스:
     * 1. 전체 발급 수량 확인 (ZCARD)
     * 2. 사용자별 발급 수량 확인 및 증가 (HINCRBY)
     * 3. Sorted Set에 추가 (ZADD NX)
     * 4. 최종 발급 수량 재확인
     * 5. 수량 초과 시 자동 롤백
     *
     * 반환값:
     * - {1, "SUCCESS", newCount, rank}: 발급 성공
     * - {0, "SOLD_OUT", currentCount}: 쿠폰 소진
     * - {0, "EXCEED_USER_LIMIT", userCount}: 사용자별 발급 제한 초과
     * - {0, "ALREADY_ISSUED", currentCount}: 이미 발급받음
     */
    private static final String COUPON_ISSUE_SCRIPT =
        "local issuedKey = KEYS[1]\n" +
        "local userCountKey = KEYS[2]\n" +
        "local userId = ARGV[1]\n" +
        "local totalQuantity = tonumber(ARGV[2]) or 0\n" +
        "local maxIssuePerUser = tonumber(ARGV[3]) or 1\n" +
        "local timestamp = ARGV[4]\n" +
        "\n" +
        "-- 1. 전체 발급 수량 확인 (ZCARD: Sorted Set 크기)\n" +
        "local currentCount = tonumber(redis.call('ZCARD', issuedKey))\n" +
        "if currentCount >= totalQuantity then\n" +
        "    return {0, 'SOLD_OUT', currentCount}\n" +
        "end\n" +
        "\n" +
        "-- 2. 사용자별 발급 수량 확인 및 증가 (HINCRBY: Hash 증가)\n" +
        "local userCount = tonumber(redis.call('HINCRBY', userCountKey, userId, 1))\n" +
        "if userCount > maxIssuePerUser then\n" +
        "    -- 롤백: 사용자 발급 수량 감소\n" +
        "    redis.call('HINCRBY', userCountKey, userId, -1)\n" +
        "    return {0, 'EXCEED_USER_LIMIT', userCount}\n" +
        "end\n" +
        "\n" +
        "-- 3. Sorted Set에 추가 (ZADD NX: Not Exists, 중복 방지)\n" +
        "-- member를 userId:userCount 형식으로 생성 (중복 발급 지원)\n" +
        "local member = userId .. ':' .. userCount\n" +
        "local added = tonumber(redis.call('ZADD', issuedKey, 'NX', timestamp, member))\n" +
        "if added == 0 then\n" +
        "    -- 동일 시각에 중복 요청 (거의 발생하지 않음) - 롤백\n" +
        "    redis.call('HINCRBY', userCountKey, userId, -1)\n" +
        "    return {0, 'ALREADY_ISSUED', currentCount}\n" +
        "end\n" +
        "\n" +
        "-- 4. 최종 발급 수량 확인 (동시 요청으로 수량 초과 가능성)\n" +
        "local newCount = tonumber(redis.call('ZCARD', issuedKey))\n" +
        "if newCount > totalQuantity then\n" +
        "    -- 수량 초과 - 롤백\n" +
        "    redis.call('ZREM', issuedKey, member)\n" +
        "    redis.call('HINCRBY', userCountKey, userId, -1)\n" +
        "    return {0, 'SOLD_OUT', newCount - 1}\n" +
        "end\n" +
        "\n" +
        "-- 5. 발급 성공 - 순위 반환 (사용자의 첫 번째 발급 기준)\n" +
        "-- userId:1 형식의 첫 번째 member로 순위 조회\n" +
        "local firstMember = userId .. ':1'\n" +
        "local rank = tonumber(redis.call('ZRANK', issuedKey, firstMember))\n" +
        "if rank == nil then\n" +
        "    -- 첫 번째 발급이면 현재 member로 순위 조회\n" +
        "    rank = tonumber(redis.call('ZRANK', issuedKey, member))\n" +
        "end\n" +
        "return {1, 'SUCCESS', newCount, rank + 1}\n";

    /**
     * 쿠폰 발급 처리 (Sorted Set + Lua Script)
     *
     * Sorted Set 기반 선착순 쿠폰 발급:
     * - 발급 순서를 타임스탬프로 기록
     * - Lua Script로 원자성 보장
     * - ZADD NX로 중복 발급 자동 방지
     *
     * 프로세스:
     * 1. 현재 시각 타임스탬프 생성 (밀리초)
     * 2. Lua Script 실행 (원자적)
     *    - 전체 발급 수량 확인
     *    - 사용자별 발급 수량 확인 및 증가
     *    - Sorted Set에 추가
     *    - 수량 초과 시 자동 롤백
     * 3. 결과 분석 및 반환
     * 4. 성공 시 TTL 설정
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @param totalQuantity 총 발급 수량
     * @param maxIssuePerUser 1인당 최대 발급 수량
     * @return IssueResult (성공 여부, 메시지, 발급 수량, 순위)
     */
    public IssueResult issue(Long couponId, Long userId, Integer totalQuantity, Integer maxIssuePerUser) {
        String issuedKey = COUPON_ISSUED_PREFIX + couponId;
        String userCountKey = COUPON_USER_COUNT_PREFIX + couponId;

        try {
            // 현재 시각 타임스탬프 (밀리초)
            long timestamp = System.currentTimeMillis();

            // Lua Script 실행 (StringRedisTemplate 사용하여 String 결과 직접 반환)
            @SuppressWarnings("unchecked")
            List<Object> result = stringRedisTemplate.execute(
                (org.springframework.data.redis.core.RedisCallback<List<Object>>) connection -> {
                    // EVAL 명령어로 Lua Script 직접 실행
                    byte[] script = COUPON_ISSUE_SCRIPT.getBytes();

                    // Keys와 Args를 하나의 배열로 병합 (Redis EVAL 명령어 형식)
                    byte[][] keysAndArgs = new byte[][]{
                        issuedKey.getBytes(),
                        userCountKey.getBytes(),
                        userId.toString().getBytes(),
                        totalQuantity.toString().getBytes(),
                        maxIssuePerUser.toString().getBytes(),
                        String.valueOf(timestamp).getBytes()
                    };

                    // EVAL script numkeys key [key ...] arg [arg ...]
                    Object evalResult = connection.eval(
                        script,
                        org.springframework.data.redis.connection.ReturnType.MULTI,
                        2,  // numkeys: 앞의 2개가 KEYS
                        keysAndArgs
                    );

                    if (evalResult instanceof List) {
                        return (List<Object>) evalResult;
                    }
                    return Collections.emptyList();
                });

            if (result == null || result.isEmpty()) {
                log.error("Lua Script 실행 결과 없음 - couponId: {}, userId: {}", couponId, userId);
                return IssueResult.failure("SCRIPT_ERROR", 0L, null);
            }

            // 결과 파싱 (StringRedisTemplate 사용으로 String 직접 반환)
            int success = parseToInt(result.get(0));
            String message = parseToString(result.get(1));
            Long count = parseToLong(result.get(2));
            Long rank = result.size() > 3 ? parseToLong(result.get(3)) : null;

            if (success == 1) {
                // 발급 성공
                setTTLIfNotExists(issuedKey);
                setTTLIfNotExists(userCountKey);

                log.info("쿠폰 발급 성공 (Sorted Set) - userId: {}, couponId: {}, rank: {}, issued: {}/{}",
                        userId, couponId, rank, count, totalQuantity);

                return IssueResult.success(count, rank);
            } else {
                // 발급 실패
                log.warn("쿠폰 발급 실패 - userId: {}, couponId: {}, reason: {}, count: {}",
                        userId, couponId, message, count);

                return IssueResult.failure(message, count, null);
            }

        } catch (Exception e) {
            log.error("쿠폰 발급 중 Redis 오류 발생 - userId: {}, couponId: {}", userId, couponId);
            log.error("에러 상세:", e);
            return IssueResult.failure("REDIS_ERROR", 0L, null);
        }
    }

    /**
     * 현재 발급된 쿠폰 수량 조회
     *
     * @param couponId 쿠폰 ID
     * @return 발급 수량 (Sorted Set 크기)
     */
    public Long getIssuedCount(Long couponId) {
        String key = COUPON_ISSUED_PREFIX + couponId;
        Long count = redisTemplate.opsForZSet().size(key);
        return count != null ? count : 0L;
    }

    /**
     * 사용자별 발급 수량 조회
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 발급 수량 (없으면 0)
     */
    public Long getUserIssuedCount(Long couponId, Long userId) {
        String key = COUPON_USER_COUNT_PREFIX + couponId;
        Object value = redisTemplate.opsForHash().get(key, userId.toString());

        if (value == null) {
            return 0L;
        }

        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        } else if (value instanceof Long) {
            return (Long) value;
        }

        return 0L;
    }

    /**
     * 사용자가 해당 쿠폰을 발급받았는지 확인
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 발급 여부 (Sorted Set 멤버 존재 여부)
     */
    public boolean hasIssued(Long couponId, Long userId) {
        String key = COUPON_ISSUED_PREFIX + couponId;
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());
        return score != null;
    }

    /**
     * 사용자의 발급 순위 조회
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 발급 순위 (1부터 시작, 발급받지 않았으면 null)
     */
    public Long getUserRank(Long couponId, Long userId) {
        String key = COUPON_ISSUED_PREFIX + couponId;
        Long rank = redisTemplate.opsForZSet().rank(key, userId.toString());
        return rank != null ? rank + 1 : null;
    }

    /**
     * 사용자의 발급 시각 조회
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 발급 시각 타임스탬프 (밀리초, 발급받지 않았으면 null)
     */
    public Long getUserIssueTimestamp(Long couponId, Long userId) {
        String key = COUPON_ISSUED_PREFIX + couponId;
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());
        return score != null ? score.longValue() : null;
    }

    /**
     * 선착순 발급 내역 조회 (상위 N명)
     *
     * @param couponId 쿠폰 ID
     * @param topN 조회할 상위 인원 수
     * @return 발급 내역 리스트 (순위순)
     */
    public List<IssueRecord> getTopIssuedUsers(Long couponId, int topN) {
        String key = COUPON_ISSUED_PREFIX + couponId;

        // Sorted Set에서 스코어와 함께 조회
        Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<Object>> tuples =
            redisTemplate.opsForZSet().rangeWithScores(key, 0, topN - 1);

        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        // DTO 변환
        int rank = 1;
        List<IssueRecord> records = new ArrayList<>();
        for (org.springframework.data.redis.core.ZSetOperations.TypedTuple<Object> tuple : tuples) {
            String userId = tuple.getValue().toString();
            Long timestamp = tuple.getScore() != null ? tuple.getScore().longValue() : null;

            records.add(IssueRecord.builder()
                .rank(rank++)
                .userId(Long.parseLong(userId))
                .issuedTimestamp(timestamp)
                .build());
        }

        return records;
    }

    /**
     * 전체 발급 내역 조회
     *
     * @param couponId 쿠폰 ID
     * @return 발급 내역 리스트 (순위순)
     */
    public List<IssueRecord> getAllIssuedUsers(Long couponId) {
        String key = COUPON_ISSUED_PREFIX + couponId;

        Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<Object>> tuples =
            redisTemplate.opsForZSet().rangeWithScores(key, 0, -1);

        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        int rank = 1;
        List<IssueRecord> records = new ArrayList<>();
        for (org.springframework.data.redis.core.ZSetOperations.TypedTuple<Object> tuple : tuples) {
            String userId = tuple.getValue().toString();
            Long timestamp = tuple.getScore() != null ? tuple.getScore().longValue() : null;

            records.add(IssueRecord.builder()
                .rank(rank++)
                .userId(Long.parseLong(userId))
                .issuedTimestamp(timestamp)
                .build());
        }

        return records;
    }

    /**
     * 쿠폰 발급 데이터 초기화
     *
     * Use Case:
     * - 쿠폰이 새로 생성되거나 재발급 시작 시
     * - 테스트 환경에서 데이터 초기화
     *
     * @param couponId 쿠폰 ID
     */
    public void initializeCoupon(Long couponId) {
        String issuedKey = COUPON_ISSUED_PREFIX + couponId;
        String userCountKey = COUPON_USER_COUNT_PREFIX + couponId;

        redisTemplate.delete(issuedKey);
        redisTemplate.delete(userCountKey);

        log.info("쿠폰 Redis 데이터 초기화 (Sorted Set) - couponId: {}", couponId);
    }

    /**
     * TTL 설정 (키가 존재하고 TTL이 없을 때만)
     *
     * @param key Redis 키
     */
    private void setTTLIfNotExists(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl != null && ttl == -1) { // TTL이 설정되지 않은 경우
            redisTemplate.expire(key, COUPON_DATA_TTL_DAYS, TimeUnit.DAYS);
        }
    }

    /**
     * 쿠폰 발급 통계 조회
     *
     * @param couponId 쿠폰 ID
     * @return 발급 통계 정보
     */
    public CouponIssueStats getIssueStats(Long couponId) {
        Long issuedCount = getIssuedCount(couponId);

        // 고유 사용자 수 (Sorted Set 크기와 동일)
        Long uniqueUsers = issuedCount;

        // 사용자별 발급 수량 총합
        String userCountKey = COUPON_USER_COUNT_PREFIX + couponId;
        Map<Object, Object> userCounts = redisTemplate.opsForHash().entries(userCountKey);
        Long totalIssueCount = userCounts.values().stream()
            .mapToLong(v -> ((Number) v).longValue())
            .sum();

        return CouponIssueStats.builder()
            .couponId(couponId)
            .issuedCount(issuedCount)
            .uniqueUserCount(uniqueUsers)
            .totalIssueCount(totalIssueCount)
            .build();
    }

    // ========== Helper Methods ==========

    /**
     * Lua Script 결과를 Integer로 파싱
     *
     * StringRedisTemplate 사용으로 결과가 String 또는 Long으로 반환됨
     *
     * @param value Lua Script 반환값
     * @return 정수값 (파싱 실패 시 0)
     */
    private int parseToInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Integer 파싱 실패, 0 반환 - value: {}", value);
            return 0;
        }
    }

    /**
     * Lua Script 결과를 Long으로 파싱
     *
     * StringRedisTemplate 사용으로 결과가 String 또는 Long으로 반환됨
     *
     * @param value Lua Script 반환값
     * @return Long값 (파싱 실패 시 0L)
     */
    private Long parseToLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Long 파싱 실패, 0L 반환 - value: {}", value);
            return 0L;
        }
    }

    /**
     * Redis 결과를 String으로 파싱
     *
     * Redis에서 byte array로 반환된 값을 String으로 변환합니다.
     *
     * @param value Redis 반환 값
     * @return String 값
     */
    private String parseToString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof byte[]) {
            return new String((byte[]) value);
        }
        if (value instanceof String) {
            return (String) value;
        }
        return value.toString();
    }

    // ========== Inner Classes ==========

    /**
     * 쿠폰 발급 결과 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class IssueResult {
        private boolean success;
        private String message;
        private Long issuedCount;
        private Long rank; // 발급 순위 (선착순)

        public static IssueResult success(Long count, Long rank) {
            return IssueResult.builder()
                .success(true)
                .message("SUCCESS")
                .issuedCount(count)
                .rank(rank)
                .build();
        }

        public static IssueResult failure(String message, Long count, Long rank) {
            return IssueResult.builder()
                .success(false)
                .message(message)
                .issuedCount(count)
                .rank(rank)
                .build();
        }
    }

    /**
     * 발급 내역 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class IssueRecord {
        private Integer rank;              // 발급 순위
        private Long userId;               // 사용자 ID
        private Long issuedTimestamp;      // 발급 시각 (밀리초)
    }

    /**
     * 쿠폰 발급 통계 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class CouponIssueStats {
        private Long couponId;
        private Long issuedCount;          // 발급된 쿠폰 수 (Sorted Set 크기)
        private Long uniqueUserCount;      // 고유 사용자 수
        private Long totalIssueCount;      // 총 발급 횟수 (중복 포함)
    }
}
