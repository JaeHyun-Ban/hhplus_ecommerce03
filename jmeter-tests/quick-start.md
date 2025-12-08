# JMeter 성능 테스트 빠른 시작 가이드

## 🚀 5분 안에 테스트 시작하기

### 1단계: JMeter 설치
```bash
brew install jmeter
```

### 2단계: 애플리케이션 준비
```bash
# Terminal 1: Redis 실행
redis-server

# Terminal 2: 애플리케이션 실행
cd /Users/banjaehyeon/Desktop/workspace/ecommerce
./gradlew bootRun
```

### 3단계: 테스트 실행
```bash
cd /Users/banjaehyeon/Desktop/workspace/ecommerce/jmeter-tests

# 모든 테스트 실행
./run-tests.sh all

# 또는 개별 테스트
./run-tests.sh coupon    # 쿠폰 발급 테스트만
./run-tests.sh ranking   # 랭킹 조회 테스트만
./run-tests.sh system    # 전체 시스템 성능 테스트만
```

### 4단계: 결과 확인
테스트가 완료되면 자동으로 HTML 리포트가 열립니다.

수동으로 열기:
```bash
open results/coupon-test-[TIMESTAMP]-report/index.html
open results/ranking-test-[TIMESTAMP]-report/index.html
```

---

## 📊 주요 지표 확인 방법

### HTML 리포트에서 확인할 지표

1. **Dashboard > Statistics**
   - Samples: 총 요청 수
   - Average: 평균 응답 시간
   - Error %: 에러율
   - Throughput: 처리량 (req/sec)

2. **Response Times Over Time**
   - 시간대별 응답 시간 그래프
   - 평균/중앙값/90th 백분위수

3. **Response Times Percentiles**
   - 90th: 상위 10% 느린 요청
   - 95th: 상위 5% 느린 요청
   - 99th: 상위 1% 느린 요청

---

## ✅ 성공 기준

### 선착순 쿠폰 발급 테스트
```
✅ 성공 응답: 정확히 100개 (HTTP 200)
⏹  쿠폰 소진: 900개 (HTTP 410 - 정상)
✅ 동시성 정확도: 100%
```

💡 **참고**: JMeter 로그에서 "Err: 900"이 표시되지만 이것은 에러가 아닙니다!
   쿠폰 소진(410)은 정상적인 비즈니스 응답입니다.

### 인기상품 랭킹 조회 테스트
```
✅ 평균 응답 시간: < 10ms
✅ P95 응답 시간: < 20ms
✅ P99 응답 시간: < 50ms
✅ TPS: > 100
✅ 에러율: < 0.1%
```

### 전체 시스템 성능 테스트
```
✅ 평균 응답 시간: < 200ms
✅ P95 응답 시간: < 500ms
✅ P99 응답 시간: < 1000ms
✅ 목표 TPS: 100
✅ 에러율: < 1%
```

---

## 🔧 트러블슈팅

### "Connection refused" 에러
```bash
# 애플리케이션이 실행 중인지 확인
curl http://localhost:8080/actuator/health

# 응답이 없으면 애플리케이션 실행
./gradlew bootRun
```

### "Redis connection failed" 에러
```bash
# Redis가 실행 중인지 확인
redis-cli ping

# PONG 응답이 없으면 Redis 실행
redis-server
```

### 쿠폰 테스트 실패 (100개 이외 발급)
```bash
# Redis 초기화
redis-cli FLUSHALL

# 애플리케이션 재시작
# Ctrl+C로 종료 후 다시 실행
./gradlew bootRun
```

---

## 📝 커스터마이징

### 동시 사용자 수 변경
`coupon-concurrency-test.jmx` 파일 열기:
```xml
<stringProp name="ThreadGroup.num_threads">1000</stringProp>
<!-- 1000을 원하는 숫자로 변경 -->
```

### 테스트 지속 시간 변경
`ranking-load-test.jmx` 파일 열기:
```xml
<stringProp name="ThreadGroup.duration">60</stringProp>
<!-- 60초를 원하는 시간으로 변경 -->
```

### 서버 주소 변경
각 JMX 파일의 `User Defined Variables` 섹션:
```xml
<stringProp name="Argument.value">http://localhost:8080</stringProp>
<!-- localhost를 실제 서버 주소로 변경 -->
```

---

## 📖 더 알아보기

상세한 설명은 [README.md](README.md)를 참조하세요.

- 테스트 시나리오 설명
- 결과 분석 방법
- 성능 측정 체크리스트
- 참고 자료
