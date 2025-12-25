// k6 테스트 공통 설정

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 테스트 사용자 데이터
export const TEST_USERS = {
  user1: { id: 1 },
  user2: { id: 2 },
  user3: { id: 3 },
  user4: { id: 4 },
  user5: { id: 5 },
};

// 테스트 상품 데이터
export const TEST_PRODUCTS = {
  product1: { id: 1 },
  product2: { id: 2 },
  product3: { id: 3 },
};

// 테스트 쿠폰 데이터
export const TEST_COUPONS = {
  coupon1: { id: 1 },
  coupon2: { id: 2 },
};

// HTTP 요청 공통 헤더
export const HEADERS = {
  'Content-Type': 'application/json',
  'Accept': 'application/json',
};

// 성능 임계값 (Thresholds)
export const THRESHOLDS = {
  // HTTP 요청 실패율 < 1%
  http_req_failed: ['rate<0.01'],

  // 95 백분위수 응답 시간
  http_req_duration: {
    fast: ['p(95)<200'],      // < 200ms: 우수
    normal: ['p(95)<500'],    // < 500ms: 양호
    slow: ['p(95)<1000'],     // < 1000ms: 허용
  },

  // HTTP 요청 성공 응답 체크
  checks: ['rate>0.99'],      // 99% 이상 성공
};

// 테스트 시나리오 옵션
export const SCENARIOS = {
  // Smoke Test: 기본 기능 확인
  smoke: {
    executor: 'constant-vus',
    vus: 1,
    duration: '30s',
  },

  // Load Test: 정상 부하
  load: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '1m', target: 50 },   // 1분 동안 50명으로 증가
      { duration: '3m', target: 50 },   // 3분 동안 50명 유지
      { duration: '1m', target: 0 },    // 1분 동안 0명으로 감소
    ],
  },

  // Stress Test: 한계 부하
  stress: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '2m', target: 100 },  // 2분 동안 100명으로 증가
      { duration: '5m', target: 100 },  // 5분 동안 100명 유지
      { duration: '2m', target: 200 },  // 2분 동안 200명으로 증가
      { duration: '5m', target: 200 },  // 5분 동안 200명 유지
      { duration: '2m', target: 0 },    // 2분 동안 0명으로 감소
    ],
  },

  // Spike Test: 급격한 부하 증가
  spike: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '10s', target: 0 },    // 10초 대기
      { duration: '10s', target: 500 },  // 10초 만에 500명으로 급증
      { duration: '3m', target: 500 },   // 3분 동안 500명 유지
      { duration: '10s', target: 0 },    // 10초 만에 0명으로 감소
    ],
  },

  // 선착순 쿠폰 발급 테스트
  couponIssue: {
    executor: 'shared-iterations',
    vus: 1000,                 // 1000명의 동시 사용자
    iterations: 1000,          // 총 1000번의 요청
    maxDuration: '30s',        // 최대 30초
  },
};

// 랜덤 사용자 ID 생성 (1~1000)
export function getRandomUserId() {
  return Math.floor(Math.random() * 1000) + 1;
}

// 랜덤 상품 ID 생성 (1~100)
export function getRandomProductId() {
  return Math.floor(Math.random() * 100) + 1;
}

// 랜덤 수량 생성 (1~5)
export function getRandomQuantity() {
  return Math.floor(Math.random() * 5) + 1;
}

// Sleep 헬퍼 (사용자 행동 시뮬레이션)
export function randomSleep(min = 1, max = 3) {
  const sleep = Math.random() * (max - min) + min;
  return sleep;
}
