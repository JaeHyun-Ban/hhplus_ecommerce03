import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, HEADERS, SCENARIOS } from '../config.js';

/**
 * Smoke Test - 기본 API 동작 확인
 *
 * 목적:
 * - 애플리케이션 기본 동작 확인
 * - API 엔드포인트 접근 가능 여부 확인
 * - 부하 테스트 전 사전 검증
 */
export const options = {
  scenarios: {
    smoke: SCENARIOS.smoke,
  },
};

export default function () {
  // 1. Health Check
  const healthResponse = http.get(`${BASE_URL}/actuator/health`);
  check(healthResponse, {
    '[Health] 서버 실행 중': (r) => r.status === 200,
    '[Health] 상태 UP': (r) => r.json('status') === 'UP',
  });

  sleep(1);

  // 2. 상품 목록 조회
  const productsResponse = http.get(`${BASE_URL}/api/products?page=0&size=5`, { headers: HEADERS });
  check(productsResponse, {
    '[Products] API 응답 성공': (r) => r.status === 200,
    '[Products] 페이징 정보 포함': (r) => r.json('pageable') !== undefined,
  });

  sleep(1);

  // 3. 쿠폰 목록 조회
  const couponsResponse = http.get(`${BASE_URL}/api/coupons/available`, { headers: HEADERS });
  check(couponsResponse, {
    '[Coupons] API 응답 성공': (r) => r.status === 200,
  });

  sleep(1);

  // 4. 인기 상품 조회 (Redis)
  const popularResponse = http.get(`${BASE_URL}/api/products/popular/realtime?topN=5`, { headers: HEADERS });
  check(popularResponse, {
    '[Popular] API 응답 성공': (r) => r.status === 200,
  });

  sleep(2);
}

export function handleSummary(data) {
  const checks = data.metrics.checks?.values?.rate || 0;
  const checksPassed = (checks * 100).toFixed(2);

  console.log('\n=== Smoke Test 결과 ===');
  console.log(`✅ 체크 통과율: ${checksPassed}%`);

  if (checks >= 0.99) {
    console.log('✅ Smoke Test 통과! 부하 테스트를 진행할 수 있습니다.');
  } else {
    console.log('❌ Smoke Test 실패! API 또는 서버 상태를 확인하세요.');
  }
  console.log('========================\n');

  return {
    'stdout': JSON.stringify(data, null, 2),
  };
}
