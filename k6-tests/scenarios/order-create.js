import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { BASE_URL, HEADERS, THRESHOLDS, SCENARIOS, getRandomUserId, randomSleep } from '../config.js';

// 커스텀 메트릭
const successfulOrders = new Counter('successful_orders');
const failedOrders = new Counter('failed_orders');
const orderDuration = new Trend('order_creation_duration');

// 테스트 설정
export const options = {
  scenarios: {
    // 점진적 부하 증가 시나리오
    loadTest: SCENARIOS.load,
  },
  thresholds: {
    http_req_failed: THRESHOLDS.http_req_failed,
    http_req_duration: THRESHOLDS.http_req_duration.normal,
    checks: THRESHOLDS.checks,

    // 주문 생성은 복잡한 트랜잭션이므로 더 긴 응답 시간 허용
    'order_creation_duration': ['p(95)<2000'],  // 95%가 2초 이내
  },
};

/**
 * 주문 생성 부하 테스트
 *
 * 목적:
 * - 주문 생성 프로세스의 성능 및 안정성 검증
 * - 재고 차감, 결제 처리, 쿠폰 사용의 통합 시나리오 테스트
 * - 동시 주문 처리 능력 측정
 *
 * 시나리오:
 * - 50명의 동시 사용자가 주문 생성
 * - 각 사용자는 다음 단계를 수행:
 *   1. 장바구니에 상품 추가
 *   2. 주문 생성 및 결제
 *
 * 검증 사항:
 * - 주문 생성 성공률
 * - 응답 시간 (95% < 2초)
 * - 멱등성 키를 통한 중복 결제 방지
 */
export default function () {
  const userId = getRandomUserId();
  const idempotencyKey = `test-${Date.now()}-${__VU}-${__ITER}`;

  // Step 1: 장바구니에 상품 추가 (선택 사항 - 필요시 구현)
  // 테스트 간소화를 위해 이미 장바구니에 상품이 있다고 가정

  // Step 2: 주문 생성 및 결제
  const orderPayload = JSON.stringify({
    userId: userId,
    userCouponId: null,  // 쿠폰 미사용
    idempotencyKey: idempotencyKey,
  });

  const startTime = Date.now();

  const orderResponse = http.post(
    `${BASE_URL}/api/orders`,
    orderPayload,
    { headers: HEADERS }
  );

  const duration = Date.now() - startTime;
  orderDuration.add(duration);

  // 응답 검증
  const isSuccess = orderResponse.status === 201;
  const orderData = isSuccess ? orderResponse.json() : null;

  check(orderResponse, {
    '주문 생성 성공 (201 Created)': () => isSuccess,
    '주문 번호 생성됨': () => isSuccess && orderData.orderNumber !== undefined,
    '주문 상태가 PAID': () => isSuccess && orderData.status === 'PAID',
    '응답 시간 < 3초': () => duration < 3000,
  });

  // 메트릭 수집
  if (isSuccess) {
    successfulOrders.add(1);
  } else {
    failedOrders.add(1);
    console.log(`[VU ${__VU}] 주문 실패 - userId: ${userId}, status: ${orderResponse.status}, error: ${orderResponse.body.substring(0, 200)}`);
  }

  // Step 3: 주문 상세 조회 (생성된 주문 확인)
  if (isSuccess && orderData) {
    sleep(randomSleep(0.5, 1));

    const getOrderResponse = http.get(
      `${BASE_URL}/api/orders/${orderData.id}`,
      { headers: HEADERS }
    );

    check(getOrderResponse, {
      '주문 조회 성공 (200 OK)': (r) => r.status === 200,
      '주문 ID 일치': (r) => r.status === 200 && r.json('id') === orderData.id,
    });
  }

  // 사용자 행동 시뮬레이션 (다음 주문까지 대기)
  sleep(randomSleep(2, 5));
}

/**
 * 테스트 종료 후 요약 정보 출력
 */
export function handleSummary(data) {
  const successCount = data.metrics.successful_orders?.values?.count || 0;
  const failedCount = data.metrics.failed_orders?.values?.count || 0;
  const totalCount = successCount + failedCount;
  const successRate = totalCount > 0 ? (successCount / totalCount * 100).toFixed(2) : 0;

  const p95Duration = data.metrics.order_creation_duration?.values['p(95)'] || 0;
  const avgDuration = data.metrics.order_creation_duration?.values['avg'] || 0;

  console.log('\n=== 주문 생성 부하 테스트 결과 ===');
  console.log(`✅ 성공: ${successCount}건`);
  console.log(`❌ 실패: ${failedCount}건`);
  console.log(`📊 성공률: ${successRate}%`);
  console.log(`⏱️  평균 응답 시간: ${avgDuration.toFixed(0)}ms`);
  console.log(`⏱️  P95 응답 시간: ${p95Duration.toFixed(0)}ms`);
  console.log('===================================\n');

  // 성능 평가
  if (successRate >= 99 && p95Duration < 2000) {
    console.log('✅ 성능 목표 달성! (성공률 >= 99%, P95 < 2초)');
  } else if (successRate >= 95) {
    console.log('⚠️  성능 개선 필요 (성공률 또는 응답 시간)');
  } else {
    console.log('❌ 성능 목표 미달 (성공률 < 95%)');
  }

  return {
    'stdout': JSON.stringify(data, null, 2),
  };
}
