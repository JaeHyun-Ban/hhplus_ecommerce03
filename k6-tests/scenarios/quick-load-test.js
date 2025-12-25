import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { BASE_URL, HEADERS } from '../config.js';

// 커스텀 메트릭
const successfulRequests = new Counter('successful_requests');
const failedRequests = new Counter('failed_requests');
const apiDuration = new Trend('api_duration');

// 빠른 부하 테스트 설정
export const options = {
  stages: [
    { duration: '30s', target: 50 },   // 30초 동안 50명으로 증가
    { duration: '1m', target: 100 },   // 1분 동안 100명으로 증가
    { duration: '30s', target: 0 },    // 30초 동안 0명으로 감소
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.95'],
  },
};

export default function () {
  // 1. 상품 목록 조회
  const startTime = Date.now();
  const response = http.get(`${BASE_URL}/api/products?page=0&size=20`, { headers: HEADERS });
  const duration = Date.now() - startTime;

  apiDuration.add(duration);

  const isSuccess = response.status === 200;

  check(response, {
    '상품 목록 조회 성공': () => isSuccess,
    '응답 시간 < 1초': () => duration < 1000,
  });

  if (isSuccess) {
    successfulRequests.add(1);
  } else {
    failedRequests.add(1);
  }

  sleep(1);
}

export function handleSummary(data) {
  const successCount = data.metrics.successful_requests?.values?.count || 0;
  const failedCount = data.metrics.failed_requests?.values?.count || 0;
  const totalCount = successCount + failedCount;
  const successRate = totalCount > 0 ? (successCount / totalCount * 100).toFixed(2) : 0;

  const p95 = data.metrics.api_duration?.values['p(95)'] || 0;
  const avg = data.metrics.api_duration?.values['avg'] || 0;
  const max = data.metrics.api_duration?.values['max'] || 0;

  console.log('\n=== 빠른 부하 테스트 결과 ===');
  console.log(`✅ 성공: ${successCount}건`);
  console.log(`❌ 실패: ${failedCount}건`);
  console.log(`📊 성공률: ${successRate}%`);
  console.log(`⏱️  평균: ${avg.toFixed(0)}ms`);
  console.log(`⏱️  P95: ${p95.toFixed(0)}ms`);
  console.log(`⏱️  최대: ${max.toFixed(0)}ms`);
  console.log('================================\n');

  return {
    'stdout': JSON.stringify(data, null, 2),
  };
}
