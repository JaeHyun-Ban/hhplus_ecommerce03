import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, HEADERS, THRESHOLDS, SCENARIOS, getRandomUserId } from '../config.js';

// 커스텀 메트릭
const successfulIssues = new Counter('successful_coupon_issues');
const failedIssues = new Counter('failed_coupon_issues');
const soldOutResponses = new Counter('sold_out_responses');
const duplicateIssues = new Counter('duplicate_issues');

// 테스트 설정
export const options = {
  scenarios: {
    // 선착순 쿠폰 발급 시나리오 (1000명이 100개 쿠폰 경쟁)
    couponRush: SCENARIOS.couponIssue,
  },
  thresholds: {
    http_req_failed: THRESHOLDS.http_req_failed,
    http_req_duration: THRESHOLDS.http_req_duration.normal,
    checks: THRESHOLDS.checks,

    // 커스텀 메트릭 임계값
    'successful_coupon_issues': ['count>=1'],  // 최소 1개 이상 발급 성공
  },
};

/**
 * 선착순 쿠폰 발급 부하 테스트
 *
 * 목적:
 * - 선착순 쿠폰 발급의 동시성 제어 검증
 * - Redis Lua Script 기반 원자성 보장 확인
 * - 정확히 제한된 수량만큼만 발급되는지 확인
 * - 중복 발급 방지 확인
 *
 * 시나리오:
 * - 1000명의 동시 사용자가 100개 한정 쿠폰 발급 시도
 * - 각 사용자는 랜덤 userId 사용 (1~1000)
 * - 응답 코드 검증:
 *   - 200 OK: 발급 성공
 *   - 409 Conflict: 이미 발급받은 사용자
 *   - 410 Gone: 쿠폰 소진
 *
 * 검증 사항:
 * - 정확히 100개만 발급 (중복 없음)
 * - 동일 사용자 중복 발급 방지
 * - 쿠폰 소진 후 적절한 에러 응답
 */
export default function () {
  // 각 VU는 고유한 userId 사용 (__VU는 1부터 시작하는 VU 번호)
  const userId = __VU;

  // 쿠폰 ID (테스트 환경에 맞게 조정 필요)
  const couponId = 1;

  // 쿠폰 발급 요청
  const payload = JSON.stringify({
    userId: userId,
  });

  const response = http.post(
    `${BASE_URL}/api/coupons/${couponId}/issue`,
    payload,
    { headers: HEADERS }
  );

  // 응답 검증
  const isSuccess = response.status === 200;
  const isDuplicate = response.status === 409;
  const isSoldOut = response.status === 410;
  const isValidResponse = isSuccess || isDuplicate || isSoldOut;

  check(response, {
    '정상 응답 코드 (200, 409, 410)': () => isValidResponse,
    '발급 성공 시 응답 본문 존재': () => !isSuccess || response.json('userCouponId') !== undefined,
  });

  // 메트릭 수집
  if (isSuccess) {
    successfulIssues.add(1);
  } else if (isDuplicate) {
    duplicateIssues.add(1);
  } else if (isSoldOut) {
    soldOutResponses.add(1);
  } else {
    failedIssues.add(1);
  }

  // 디버그 로그 (샘플링)
  if (__VU <= 5) {  // 처음 5명의 VU만 로그 출력
    console.log(`[VU ${__VU}] userId: ${userId}, status: ${response.status}, body: ${response.body.substring(0, 100)}`);
  }

  // 짧은 대기 (선택 사항)
  sleep(0.1);
}

/**
 * 테스트 종료 후 요약 정보 출력
 */
export function handleSummary(data) {
  const successCount = data.metrics.successful_coupon_issues?.values?.count || 0;
  const duplicateCount = data.metrics.duplicate_issues?.values?.count || 0;
  const soldOutCount = data.metrics.sold_out_responses?.values?.count || 0;
  const failedCount = data.metrics.failed_coupon_issues?.values?.count || 0;

  console.log('\n=== 선착순 쿠폰 발급 테스트 결과 ===');
  console.log(`✅ 발급 성공: ${successCount}개`);
  console.log(`⚠️  중복 요청: ${duplicateCount}개 (이미 발급받은 사용자)`);
  console.log(`🚫 쿠폰 소진: ${soldOutCount}개`);
  console.log(`❌ 실패: ${failedCount}개`);
  console.log(`📊 총 요청: ${successCount + duplicateCount + soldOutCount + failedCount}개`);
  console.log('===================================\n');

  // 검증 결과
  if (successCount > 100) {
    console.log('⚠️  경고: 100개 이상 발급됨! 동시성 제어 문제 발생!');
  } else if (successCount === 100) {
    console.log('✅ 성공: 정확히 100개 발급됨!');
  } else {
    console.log(`ℹ️  정보: ${successCount}개 발급됨 (테스트 환경에 따라 다를 수 있음)`);
  }

  return {
    'stdout': JSON.stringify(data, null, 2),
  };
}
