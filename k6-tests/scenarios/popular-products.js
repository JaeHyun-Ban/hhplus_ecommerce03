import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { BASE_URL, HEADERS, THRESHOLDS, SCENARIOS, randomSleep } from '../config.js';

// 커스텀 메트릭
const successfulRequests = new Counter('successful_popular_requests');
const failedRequests = new Counter('failed_popular_requests');
const dbPopularDuration = new Trend('db_popular_duration');
const redisPopularDuration = new Trend('redis_popular_duration');
const redisStatsPopularDuration = new Trend('redis_stats_popular_duration');

// 테스트 설정
export const options = {
  scenarios: {
    // 스파이크 테스트 시나리오 (급격한 부하 증가)
    spikeTest: SCENARIOS.spike,
  },
  thresholds: {
    http_req_failed: THRESHOLDS.http_req_failed,
    http_req_duration: THRESHOLDS.http_req_duration.fast,
    checks: THRESHOLDS.checks,

    // Redis 캐시 기반이므로 매우 빠른 응답 기대
    'redis_popular_duration': ['p(95)<100'],         // Redis P95 < 100ms
    'redis_stats_popular_duration': ['p(95)<150'],   // Redis Stats P95 < 150ms
    'db_popular_duration': ['p(95)<500'],            // DB P95 < 500ms
  },
};

/**
 * 인기 상품 조회 부하 테스트
 *
 * 목적:
 * - Redis 캐시 기반 인기 상품 조회 성능 검증
 * - DB 기반 인기 상품 조회와 비교
 * - 급격한 트래픽 증가 시 안정성 확인
 *
 * 시나리오:
 * - 최대 500명의 동시 사용자가 급격히 증가
 * - 각 사용자는 다음 API를 조회:
 *   1. 실시간 인기 상품 (Redis Sorted Set)
 *   2. 실시간 인기 상품 통계 포함 (Redis + 상품 정보)
 *   3. DB 기반 인기 상품 (최근 3일 집계)
 *
 * 검증 사항:
 * - Redis 조회 응답 시간 (P95 < 100ms)
 * - Redis Stats 조회 응답 시간 (P95 < 150ms)
 * - DB 조회 응답 시간 (P95 < 500ms)
 * - 높은 처리량 유지
 */
export default function () {
  // Step 1: 실시간 인기 상품 조회 (Redis 기반)
  const topN = 10;

  const redisStartTime = Date.now();

  const redisResponse = http.get(
    `${BASE_URL}/api/products/popular/realtime?topN=${topN}`,
    { headers: HEADERS }
  );

  const redisDuration = Date.now() - redisStartTime;
  redisPopularDuration.add(redisDuration);

  const isRedisSuccess = redisResponse.status === 200;
  const redisData = isRedisSuccess ? redisResponse.json() : null;

  check(redisResponse, {
    '[Redis] 조회 성공 (200 OK)': () => isRedisSuccess,
    '[Redis] 상품 데이터 존재': () => isRedisSuccess && Array.isArray(redisData) && redisData.length > 0,
    '[Redis] 최대 개수 준수': () => isRedisSuccess && redisData.length <= topN,
    '[Redis] 응답 시간 < 200ms (캐시 성능)': () => redisDuration < 200,
  });

  if (isRedisSuccess) {
    successfulRequests.add(1);
  } else {
    failedRequests.add(1);
  }

  sleep(randomSleep(0.3, 0.7));

  // Step 2: 실시간 인기 상품 조회 (통계 포함)
  const statsStartTime = Date.now();

  const statsResponse = http.get(
    `${BASE_URL}/api/products/popular/realtime/stats?topN=${topN}`,
    { headers: HEADERS }
  );

  const statsDuration = Date.now() - statsStartTime;
  redisStatsPopularDuration.add(statsDuration);

  const isStatsSuccess = statsResponse.status === 200;
  const statsData = isStatsSuccess ? statsResponse.json() : null;

  check(statsResponse, {
    '[Redis Stats] 조회 성공 (200 OK)': () => isStatsSuccess,
    '[Redis Stats] 상품 데이터 존재': () => isStatsSuccess && Array.isArray(statsData) && statsData.length > 0,
    '[Redis Stats] 통계 정보 포함': () => {
      if (!isStatsSuccess || !statsData || statsData.length === 0) return false;
      const firstItem = statsData[0];
      return firstItem.product !== undefined && firstItem.salesCount !== undefined && firstItem.rank !== undefined;
    },
    '[Redis Stats] 응답 시간 < 300ms': () => statsDuration < 300,
  });

  if (isStatsSuccess) {
    successfulRequests.add(1);
  } else {
    failedRequests.add(1);
  }

  sleep(randomSleep(0.3, 0.7));

  // Step 3: DB 기반 인기 상품 조회 (최근 3일 집계)
  const dbStartTime = Date.now();

  const dbResponse = http.get(
    `${BASE_URL}/api/products/popular`,
    { headers: HEADERS }
  );

  const dbDuration = Date.now() - dbStartTime;
  dbPopularDuration.add(dbDuration);

  const isDbSuccess = dbResponse.status === 200;
  const dbData = isDbSuccess ? dbResponse.json() : null;

  check(dbResponse, {
    '[DB] 조회 성공 (200 OK)': () => isDbSuccess,
    '[DB] 상품 데이터 존재': () => isDbSuccess && Array.isArray(dbData),
    '[DB] 응답 시간 < 1초': () => dbDuration < 1000,
  });

  if (isDbSuccess) {
    successfulRequests.add(1);
  } else {
    failedRequests.add(1);
  }

  // 사용자 행동 시뮬레이션 (다음 조회까지 대기)
  sleep(randomSleep(1, 2));
}

/**
 * 테스트 종료 후 요약 정보 출력
 */
export function handleSummary(data) {
  const successCount = data.metrics.successful_popular_requests?.values?.count || 0;
  const failedCount = data.metrics.failed_popular_requests?.values?.count || 0;
  const totalCount = successCount + failedCount;
  const successRate = totalCount > 0 ? (successCount / totalCount * 100).toFixed(2) : 0;

  const redisP95 = data.metrics.redis_popular_duration?.values['p(95)'] || 0;
  const redisAvg = data.metrics.redis_popular_duration?.values['avg'] || 0;
  const redisStatsP95 = data.metrics.redis_stats_popular_duration?.values['p(95)'] || 0;
  const redisStatsAvg = data.metrics.redis_stats_popular_duration?.values['avg'] || 0;
  const dbP95 = data.metrics.db_popular_duration?.values['p(95)'] || 0;
  const dbAvg = data.metrics.db_popular_duration?.values['avg'] || 0;

  const httpReqRate = data.metrics.http_reqs?.values?.rate || 0;

  console.log('\n=== 인기 상품 조회 부하 테스트 결과 ===');
  console.log(`✅ 성공: ${successCount}건`);
  console.log(`❌ 실패: ${failedCount}건`);
  console.log(`📊 성공률: ${successRate}%`);
  console.log(`\n[Redis 실시간 인기 상품]`);
  console.log(`  ⏱️  평균: ${redisAvg.toFixed(0)}ms`);
  console.log(`  ⏱️  P95: ${redisP95.toFixed(0)}ms`);
  console.log(`\n[Redis 실시간 인기 상품 + 통계]`);
  console.log(`  ⏱️  평균: ${redisStatsAvg.toFixed(0)}ms`);
  console.log(`  ⏱️  P95: ${redisStatsP95.toFixed(0)}ms`);
  console.log(`\n[DB 기반 인기 상품]`);
  console.log(`  ⏱️  평균: ${dbAvg.toFixed(0)}ms`);
  console.log(`  ⏱️  P95: ${dbP95.toFixed(0)}ms`);
  console.log(`\n📈 처리량: ${httpReqRate.toFixed(2)} req/s`);
  console.log('===================================\n');

  // 성능 비교 분석
  const speedupFactor = dbP95 > 0 ? (dbP95 / redisP95).toFixed(1) : 0;
  console.log(`\n💡 성능 분석:`);
  console.log(`  Redis는 DB보다 ${speedupFactor}x 빠름 (P95 기준)`);

  // 성능 평가
  if (successRate >= 99 && redisP95 < 100 && redisStatsP95 < 150) {
    console.log(`  ✅ Redis 캐시 성능 우수 (P95 < 100ms)`);
  } else if (redisP95 < 200) {
    console.log(`  ⚠️  Redis 캐시 성능 양호 (P95 < 200ms)`);
  } else {
    console.log(`  ❌ Redis 캐시 성능 개선 필요`);
  }

  if (dbP95 < 500) {
    console.log(`  ✅ DB 집계 성능 우수 (P95 < 500ms)`);
  } else if (dbP95 < 1000) {
    console.log(`  ⚠️  DB 집계 성능 양호 (P95 < 1초)`);
  } else {
    console.log(`  ❌ DB 집계 성능 개선 필요`);
  }

  return {
    'stdout': JSON.stringify(data, null, 2),
  };
}
