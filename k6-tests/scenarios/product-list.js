import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { BASE_URL, HEADERS, THRESHOLDS, SCENARIOS, randomSleep } from '../config.js';

// 커스텀 메트릭
const successfulRequests = new Counter('successful_product_requests');
const failedRequests = new Counter('failed_product_requests');
const productListDuration = new Trend('product_list_duration');
const productDetailDuration = new Trend('product_detail_duration');

// 테스트 설정
export const options = {
  scenarios: {
    // 스트레스 테스트 시나리오 (높은 부하)
    stressTest: SCENARIOS.stress,
  },
  thresholds: {
    http_req_failed: THRESHOLDS.http_req_failed,
    http_req_duration: THRESHOLDS.http_req_duration.fast,  // 조회는 빠른 응답 기대
    checks: THRESHOLDS.checks,

    // 상품 조회는 빠른 응답이 중요
    'product_list_duration': ['p(95)<500'],      // 목록 조회 P95 < 500ms
    'product_detail_duration': ['p(95)<300'],    // 상세 조회 P95 < 300ms
  },
};

/**
 * 상품 조회 부하 테스트
 *
 * 목적:
 * - 상품 목록/상세 조회 API의 처리량 및 응답 시간 측정
 * - 높은 동시 접속 시 성능 검증
 * - 페이징 처리 성능 확인
 *
 * 시나리오:
 * - 최대 200명의 동시 사용자가 상품 조회
 * - 각 사용자는 다음 행동을 반복:
 *   1. 상품 목록 조회 (페이징)
 *   2. 특정 상품 상세 조회
 *   3. 카테고리별 상품 조회
 *
 * 검증 사항:
 * - 목록 조회 응답 시간 (P95 < 500ms)
 * - 상세 조회 응답 시간 (P95 < 300ms)
 * - 높은 성공률 유지 (> 99%)
 */
export default function () {
  // Step 1: 상품 목록 조회 (첫 페이지)
  const page = Math.floor(Math.random() * 5);  // 0~4 페이지 랜덤
  const size = 20;

  const listStartTime = Date.now();

  const listResponse = http.get(
    `${BASE_URL}/api/products?page=${page}&size=${size}`,
    { headers: HEADERS }
  );

  const listDuration = Date.now() - listStartTime;
  productListDuration.add(listDuration);

  const isListSuccess = listResponse.status === 200;
  const listData = isListSuccess ? listResponse.json() : null;

  check(listResponse, {
    '상품 목록 조회 성공 (200 OK)': () => isListSuccess,
    '상품 데이터 존재': () => isListSuccess && listData.content && listData.content.length > 0,
    '페이징 정보 포함': () => isListSuccess && listData.totalElements !== undefined,
    '목록 조회 응답 시간 < 1초': () => listDuration < 1000,
  });

  if (isListSuccess) {
    successfulRequests.add(1);
  } else {
    failedRequests.add(1);
  }

  sleep(randomSleep(0.5, 1));

  // Step 2: 상품 상세 조회
  // 목록에서 랜덤 상품 선택 (없으면 고정 ID 사용)
  let productId = 1;
  if (isListSuccess && listData.content && listData.content.length > 0) {
    const randomIndex = Math.floor(Math.random() * listData.content.length);
    productId = listData.content[randomIndex].id;
  }

  const detailStartTime = Date.now();

  const detailResponse = http.get(
    `${BASE_URL}/api/products/${productId}`,
    { headers: HEADERS }
  );

  const detailDuration = Date.now() - detailStartTime;
  productDetailDuration.add(detailDuration);

  const isDetailSuccess = detailResponse.status === 200;
  const detailData = isDetailSuccess ? detailResponse.json() : null;

  check(detailResponse, {
    '상품 상세 조회 성공 (200 OK)': () => isDetailSuccess,
    '상품 ID 일치': () => isDetailSuccess && detailData.id === productId,
    '상품 정보 완전함': () => isDetailSuccess && detailData.name && detailData.price !== undefined,
    '상세 조회 응답 시간 < 500ms': () => detailDuration < 500,
  });

  if (isDetailSuccess) {
    successfulRequests.add(1);
  } else {
    failedRequests.add(1);
  }

  sleep(randomSleep(0.5, 1));

  // Step 3: 카테고리별 상품 조회
  const categoryId = Math.floor(Math.random() * 5) + 1;  // 1~5 카테고리

  const categoryResponse = http.get(
    `${BASE_URL}/api/products?categoryId=${categoryId}&page=0&size=20`,
    { headers: HEADERS }
  );

  const isCategorySuccess = categoryResponse.status === 200;

  check(categoryResponse, {
    '카테고리별 조회 성공 (200 OK)': () => isCategorySuccess,
  });

  if (isCategorySuccess) {
    successfulRequests.add(1);
  } else {
    failedRequests.add(1);
  }

  // 사용자 행동 시뮬레이션 (다음 조회까지 대기)
  sleep(randomSleep(1, 3));
}

/**
 * 테스트 종료 후 요약 정보 출력
 */
export function handleSummary(data) {
  const successCount = data.metrics.successful_product_requests?.values?.count || 0;
  const failedCount = data.metrics.failed_product_requests?.values?.count || 0;
  const totalCount = successCount + failedCount;
  const successRate = totalCount > 0 ? (successCount / totalCount * 100).toFixed(2) : 0;

  const listP95 = data.metrics.product_list_duration?.values['p(95)'] || 0;
  const listAvg = data.metrics.product_list_duration?.values['avg'] || 0;
  const detailP95 = data.metrics.product_detail_duration?.values['p(95)'] || 0;
  const detailAvg = data.metrics.product_detail_duration?.values['avg'] || 0;

  const httpReqRate = data.metrics.http_reqs?.values?.rate || 0;

  console.log('\n=== 상품 조회 부하 테스트 결과 ===');
  console.log(`✅ 성공: ${successCount}건`);
  console.log(`❌ 실패: ${failedCount}건`);
  console.log(`📊 성공률: ${successRate}%`);
  console.log(`\n[목록 조회]`);
  console.log(`  ⏱️  평균: ${listAvg.toFixed(0)}ms`);
  console.log(`  ⏱️  P95: ${listP95.toFixed(0)}ms`);
  console.log(`\n[상세 조회]`);
  console.log(`  ⏱️  평균: ${detailAvg.toFixed(0)}ms`);
  console.log(`  ⏱️  P95: ${detailP95.toFixed(0)}ms`);
  console.log(`\n📈 처리량: ${httpReqRate.toFixed(2)} req/s`);
  console.log('===================================\n');

  // 성능 평가
  if (successRate >= 99 && listP95 < 500 && detailP95 < 300) {
    console.log('✅ 성능 목표 달성! (성공률 >= 99%, 목록 P95 < 500ms, 상세 P95 < 300ms)');
  } else if (successRate >= 95) {
    console.log('⚠️  성능 개선 필요 (응답 시간 개선 권장)');
  } else {
    console.log('❌ 성능 목표 미달 (성공률 또는 응답 시간)');
  }

  return {
    'stdout': JSON.stringify(data, null, 2),
  };
}
