# JMeter 성능 테스트 가이드

## 📋 목차
1. [JMeter 설치](#1-jmeter-설치)
2. [테스트 시나리오](#2-테스트-시나리오)
3. [테스트 실행](#3-테스트-실행)
4. [결과 분석](#4-결과-분석)

---

## 1. JMeter 설치

### macOS (Homebrew)
```bash
brew install jmeter
```

### 수동 설치
1. [Apache JMeter 다운로드](https://jmeter.apache.org/download_jmeter.cgi)
2. 압축 해제 후 `bin/jmeter` 실행

### 설치 확인
```bash
jmeter -v
# Apache JMeter 5.6.3 출력 확인
```

---

## 2. 테스트 시나리오

> **중요**: 각 테스트는 서로 다른 목적을 가지며 중복이 아닙니다!
> 자세한 비교는 [TEST_COMPARISON.md](TEST_COMPARISON.md)를 참고하세요.

### 시나리오 1: 선착순 쿠폰 발급 동시성 테스트 ⚡
- **목적**: **동시성 제어 정확도** 검증
- **파일**: `coupon-concurrency-test.jmx`
- **설정**: 1,000명이 5초 내에 100개 쿠폰 요청
- **핵심 검증**:
  - ✅ 정확히 100개만 발급되는가? (Redisson 분산 락)
  - ✅ Race Condition 방지
  - ✅ 데이터 정합성 (Redis ↔ DB)

### 시나리오 2: 인기상품 랭킹 조회 부하 테스트 📊
- **목적**: **Redis 캐시 성능** 측정
- **파일**: `ranking-load-test.jmx`
- **설정**: 100 TPS, 60초 지속
- **핵심 검증**:
  - ✅ 평균 응답 시간 < 10ms
  - ✅ P95 < 20ms, P99 < 50ms
  - ✅ TPS 100 이상 유지

### 시나리오 3: 전체 시스템 성능 테스트 🌐
- **목적**: **실제 사용자 행동 패턴** 시뮬레이션
- **파일**: `full-system-performance-test.jmx`
- **설정**:
  - 50명 동시 사용자, 5분 지속
  - 6가지 API 혼합 (확률 기반)
  - Think Time 적용 (1000ms ± 500ms)
- **시나리오 비율**:
  - 상품 목록 조회: 60%
  - 상품 상세 조회: 50%
  - 인기상품 랭킹: 40%
  - 장바구니 추가: 30%
  - 주문 생성: 20%
  - 쿠폰 발급: 10%
- **핵심 검증**:
  - ✅ 시스템 전체 안정성
  - ✅ 여러 API 간 상호작용
  - ✅ 실제 트래픽 패턴에서의 성능

---

## 3. 테스트 실행

### ⚠️ 사전 준비 (필수!)

JMeter 테스트 전에 **반드시** 애플리케이션을 실행해야 합니다.
그렇지 않으면 `Connection refused` 에러가 발생합니다.

```bash
# 터미널 1: 애플리케이션 실행
cd /Users/banjaehyeon/Desktop/workspace/ecommerce
./gradlew bootRun

# 터미널 2: JMeter 테스트 (애플리케이션 시작 후)
cd /Users/banjaehyeon/Desktop/workspace/ecommerce/jmeter-tests
./run-tests.sh all
```

> 📖 자세한 내용은 [START_APP.md](START_APP.md)를 참고하세요.

### 자동화 스크립트 사용 (권장) ⭐️
```bash
cd /Users/banjaehyeon/Desktop/workspace/ecommerce/jmeter-tests

# 모든 테스트 실행
./run-tests.sh all

# 개별 테스트 실행
./run-tests.sh coupon    # 쿠폰 발급 동시성 테스트
./run-tests.sh ranking   # 랭킹 조회 부하 테스트
./run-tests.sh system    # 전체 시스템 성능 테스트
```

**자동화 스크립트 기능**:
- JMeter 설치 확인
- 애플리케이션 헬스체크
- Redis 연결 확인
- 테스트 실행 및 HTML 리포트 자동 생성
- 테스트 결과 요약 출력
- 오래된 결과 파일 자동 정리 (30일 이상)

### GUI 모드 (테스트 작성/디버깅용)
```bash
cd /Users/banjaehyeon/Desktop/workspace/ecommerce/jmeter-tests

# JMeter GUI 실행
jmeter -t coupon-concurrency-test.jmx
```

### CLI 모드 (수동 실행)
```bash
# 1. 선착순 쿠폰 발급 테스트
jmeter -n -t coupon-concurrency-test.jmx \
  -l results/coupon-test-$(date +%Y%m%d_%H%M%S).jtl \
  -e -o results/coupon-report-$(date +%Y%m%d_%H%M%S)

# 2. 랭킹 조회 부하 테스트
jmeter -n -t ranking-load-test.jmx \
  -l results/ranking-test-$(date +%Y%m%d_%H%M%S).jtl \
  -e -o results/ranking-report-$(date +%Y%m%d_%H%M%S)

# 3. 전체 시스템 성능 테스트
jmeter -n -t full-system-performance-test.jmx \
  -l results/full-system-test-$(date +%Y%m%d_%H%M%S).jtl \
  -e -o results/full-system-report-$(date +%Y%m%d_%H%M%S)
```

### 옵션 설명
- `-n`: CLI 모드 (GUI 없이 실행)
- `-t`: 테스트 계획 파일
- `-l`: 결과 로그 파일 (.jtl)
- `-e`: 테스트 후 리포트 생성
- `-o`: HTML 리포트 출력 디렉토리

---

## 4. 결과 분석

### HTML 리포트 열기
```bash
# 생성된 리포트 열기
open results/coupon-report-20251204_153000/index.html
```

### 주요 지표 확인

#### 1) Summary Report
- **Samples**: 총 요청 수
- **Average**: 평균 응답 시간 (ms)
- **Min/Max**: 최소/최대 응답 시간
- **90th, 95th, 99th pct**: 백분위수 응답 시간
- **Error %**: 에러율
- **Throughput**: 처리량 (req/sec)

#### 2) Response Time Graph
- 시간대별 응답 시간 분포
- 평균/중앙값/90th 백분위수

#### 3) Transactions Per Second
- 시간대별 TPS
- 목표: 안정적인 처리량 유지

### 성공 기준

#### 선착순 쿠폰 발급
```
✅ 성공 응답: 정확히 100개 (HTTP 200)
⏹  쿠폰 소진: 900개 (HTTP 410 - 정상적인 비즈니스 응답)
✅ 동시성 정확도: 100%
✅ 에러율: 0%
```

**중요**: 900개의 410 응답은 에러가 아닙니다!
- 1,000명이 100개 쿠폰을 요청하므로 900명은 당연히 쿠폰 소진 메시지를 받습니다
- 410 GONE은 RESTful API에서 리소스 소진 시 적절한 상태 코드입니다
- JMeter에서 "Err: 900"로 표시되지만, 이것은 정상적인 비즈니스 로직입니다

#### 인기상품 랭킹 조회
```
✅ 평균 응답 시간: < 10ms
✅ P95 응답 시간: < 20ms
✅ P99 응답 시간: < 50ms
✅ TPS: > 100
✅ 에러율: < 0.1%
```

#### 전체 시스템 성능 테스트
```
✅ 평균 응답 시간: < 200ms
✅ P95 응답 시간: < 500ms
✅ P99 응답 시간: < 1000ms
✅ 목표 TPS: 100
✅ 에러율: < 1%
✅ 시스템 전반적인 안정성 확인
```

---

## 5. 테스트 환경 설정

### 애플리케이션 실행
```bash
# 1. Redis 실행 확인
redis-cli ping
# PONG 출력 확인

# 2. 애플리케이션 실행
./gradlew bootRun

# 3. 헬스체크
curl http://localhost:8080/actuator/health
```

### 테스트 데이터 준비

#### 쿠폰 생성 (선택사항)
```bash
# POST /api/coupons
curl -X POST http://localhost:8080/api/coupons \
  -H "Content-Type: application/json" \
  -d '{
    "code": "JMETER_TEST",
    "name": "JMeter 테스트 쿠폰",
    "totalQuantity": 100,
    "maxIssuePerUser": 1
  }'
```

#### 사용자 계정
- JMeter 테스트에서는 랜덤 userId 사용 (1-10000)
- 실제 DB에 존재하는 사용자 ID 사용 권장

---

## 6. 커스터마이징

### 동시 사용자 수 변경
JMX 파일에서 `Thread Group` 설정 수정:
```xml
<ThreadGroup>
  <stringProp name="ThreadGroup.num_threads">1000</stringProp>  <!-- 사용자 수 -->
  <stringProp name="ThreadGroup.ramp_time">10</stringProp>       <!-- 증가 시간(초) -->
  <stringProp name="LoopController.loops">1</stringProp>         <!-- 반복 횟수 -->
</ThreadGroup>
```

### 서버 주소 변경
`User Defined Variables`에서 수정:
```xml
<Arguments>
  <Argument>
    <stringProp name="Argument.name">BASE_URL</stringProp>
    <stringProp name="Argument.value">http://localhost:8080</stringProp>
  </Argument>
</Arguments>
```

---

## 7. 트러블슈팅

### Connection Refused
```
문제: java.net.ConnectException: Connection refused
해결: 애플리케이션이 실행 중인지 확인
```

### Out of Memory
```
문제: JMeter OutOfMemoryError
해결: JMeter 힙 메모리 증가
export HEAP="-Xms1g -Xmx4g"
jmeter -n -t test.jmx ...
```

### Too Many Open Files
```
문제: Too many open files
해결: macOS 파일 디스크립터 제한 증가
ulimit -n 10000
```

---

## 8. 성능 측정 체크리스트

### 테스트 전
- [ ] Redis 실행 확인
- [ ] 애플리케이션 실행 확인
- [ ] 테스트 데이터 준비
- [ ] 이전 테스트 결과 백업

### 테스트 중
- [ ] 시스템 리소스 모니터링 (CPU, 메모리)
- [ ] 애플리케이션 로그 모니터링
- [ ] Redis 메모리 사용량 확인

### 테스트 후
- [ ] HTML 리포트 확인
- [ ] 에러 로그 분석
- [ ] 성공 기준 달성 여부 확인
- [ ] 결과 문서화

---

## 9. 참고 자료

- [Apache JMeter 공식 문서](https://jmeter.apache.org/usermanual/index.html)
- [JMeter Best Practices](https://jmeter.apache.org/usermanual/best-practices.html)
- [Performance Testing Guide](https://jmeter.apache.org/usermanual/boss.html)
