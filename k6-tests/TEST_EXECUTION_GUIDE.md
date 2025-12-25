# 부하 테스트 실행 가이드

## 사전 준비

### 1. 테스트 데이터 생성

```bash
# MySQL에 접속
mysql -u root -p ecommerce

# 테스트 데이터 생성 스크립트 실행
source k6-tests/setup-test-data.sql

# 또는
mysql -u root -p ecommerce < k6-tests/setup-test-data.sql
```

생성되는 데이터:
- **사용자**: 1,000명 (ID: 1~1000, 초기 잔액: 10만원)
- **카테고리**: 5개
- **상품**: 100개 (각 재고 1,000개)
- **쿠폰**: 2개 (100개 한정, 500개 한정)
- **장바구니**: 처음 100명 사용자 대상 (각 1~3개 상품)

### 2. Redis 초기화 (선택 사항)

```bash
redis-cli FLUSHALL
```

### 3. 애플리케이션 실행 확인

```bash
# 애플리케이션 실행
./gradlew bootRun

# 또는 IDE에서 실행

# Health Check
curl http://localhost:8080/actuator/health
# 예상 응답: {"status":"UP"}
```

## 테스트 실행

### Phase 1: Smoke Test (사전 검증)

**목적**: 모든 API가 정상 동작하는지 확인

```bash
cd k6-tests
k6 run scenarios/smoke-test.js
```

**예상 결과**:
```
✅ 체크 통과율: 100.00%
✅ Smoke Test 통과! 부하 테스트를 진행할 수 있습니다.
```

### Phase 2: 상품 조회 부하 테스트

**목적**: 상품 목록/상세 조회 성능 측정

```bash
k6 run scenarios/product-list.js
```

**예상 결과**:
- ✅ 성공률: > 99%
- ⏱️ 목록 조회 P95: < 500ms
- ⏱️ 상세 조회 P95: < 300ms

**모니터링**: 약 16분 소요 (스트레스 테스트)

### Phase 3: 인기 상품 조회 부하 테스트

**목적**: Redis 캐시 vs DB 성능 비교

```bash
k6 run scenarios/popular-products.js
```

**예상 결과**:
- ✅ Redis P95: < 100ms
- ✅ Redis Stats P95: < 150ms
- ✅ DB P95: < 500ms
- 💡 Redis는 DB보다 5~10배 빠름

**모니터링**: 약 4분 소요 (스파이크 테스트)

### Phase 4: 선착순 쿠폰 발급 테스트

**목적**: 동시성 제어 검증 (가장 중요!)

```bash
# ⚠️ 중요: 테스트 전 쿠폰 데이터 확인
curl http://localhost:8080/api/coupons/1

# 테스트 실행
k6 run scenarios/coupon-issue.js
```

**예상 결과**:
- ✅ 발급 성공: 정확히 100개
- ⚠️ 중복 요청: 0개
- 🚫 쿠폰 소진: 900개

**검증 사항**:
1. 동시성 제어: 100개 초과 발급 방지
2. 중복 발급 방지
3. 적절한 에러 응답 (410 Gone)

**주의**: 이 테스트는 한 번만 실행 가능 (쿠폰 소진)
- 재실행 시 쿠폰 데이터 재설정 필요

```sql
-- 쿠폰 재설정
UPDATE coupon SET issued_quantity = 0 WHERE id = 1;
DELETE FROM user_coupon WHERE coupon_id = 1;

-- Redis 초기화
redis-cli DEL "coupon:issue:1"
redis-cli DEL "coupon:issued:1:*"
```

### Phase 5: 주문 생성 부하 테스트

**목적**: 주문 프로세스 성능 및 안정성 검증

```bash
k6 run scenarios/order-create.js
```

**예상 결과**:
- ✅ 성공률: > 95%
- ⏱️ P95 응답 시간: < 2초
- 📊 처리량: > 10 TPS

**모니터링**: 약 5분 소요

**주의사항**:
- 재고가 부족하면 주문 실패 증가
- 잔액 부족 시 주문 실패

## 테스트 결과 저장

### JSON 형식으로 저장

```bash
k6 run --out json=results/product-list-result.json scenarios/product-list.js
k6 run --out json=results/popular-products-result.json scenarios/popular-products.js
k6 run --out json=results/coupon-issue-result.json scenarios/coupon-issue.js
k6 run --out json=results/order-create-result.json scenarios/order-create.js
```

### HTML 리포트 생성 (k6-reporter 필요)

```bash
# k6-reporter 설치
npm install -g k6-to-html

# 리포트 생성
k6 run --out json=results/test-result.json scenarios/product-list.js
k6-to-html results/test-result.json -o results/test-report.html
```

## 모니터링 체크리스트

테스트 실행 중 다음 항목을 모니터링하세요:

### 애플리케이션
- [ ] CPU 사용률 (< 80%)
- [ ] 메모리 사용률 (< 80%)
- [ ] 스레드 풀 상태
- [ ] GC 빈도 및 시간

```bash
# JVM 모니터링 (VisualVM, JConsole 등)
jconsole
```

### 데이터베이스
- [ ] 연결 수
- [ ] 슬로우 쿼리 로그
- [ ] Lock 대기 시간

```sql
-- 실행 중인 쿼리 확인
SHOW PROCESSLIST;

-- 슬로우 쿼리 확인
SELECT * FROM mysql.slow_log ORDER BY start_time DESC LIMIT 10;
```

### Redis
- [ ] 명령어 처리 속도
- [ ] 메모리 사용률
- [ ] 초당 연산 수

```bash
# Redis 모니터링
redis-cli INFO stats
redis-cli INFO memory
redis-cli MONITOR  # 실시간 명령어 모니터링
```

## 문제 해결

### Connection Refused
```bash
# 애플리케이션 실행 확인
curl http://localhost:8080/actuator/health

# 포트 확인
lsof -i :8080
```

### Too Many Open Files
```bash
# macOS/Linux
ulimit -n 10000
```

### 테스트 중단 방법
```bash
# Ctrl+C 누르기
# k6는 그래이스풀하게 종료됨
```

## 테스트 시나리오별 요약

| 테스트 | VUs | Duration | 목적 | 데이터 필요 |
|--------|-----|----------|------|------------|
| **Smoke Test** | 1 | 30s | API 동작 확인 | 선택 |
| **Product List** | 0→200 | 16분 | 조회 성능 측정 | 필수 |
| **Popular Products** | 0→500 | 4분 | Redis vs DB 비교 | 선택 |
| **Coupon Issue** | 1000 | 30s | 동시성 제어 검증 | 필수 |
| **Order Create** | 0→50 | 5분 | 주문 프로세스 검증 | 필수 |

## 다음 단계

1. ✅ 테스트 데이터 생성
2. ✅ Smoke Test 통과 확인
3. ✅ 각 시나리오별 부하 테스트 실행
4. 📊 결과 분석 및 보고서 작성
5. 🔧 성능 개선 작업
6. 🔄 재테스트

---

**문의 사항이나 이슈 발생 시 로그를 확인하세요**:
- 애플리케이션 로그: `logs/application.log`
- MySQL 에러 로그: `/var/log/mysql/error.log`
- Redis 로그: `/var/log/redis/redis-server.log`
