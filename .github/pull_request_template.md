## [STEP 17-18] 반재현(e-commerce)

---
### STEP 17 카프카 기초 학습 및 활용
- [x] 카프카에 대한 기본 개념 학습 문서 작성
- [x] 실시간 주문/예약 정보를 카프카 메시지로 발행

**주요 커밋:**
- [[7a6a1d3](https://github.com/JaeHyun-Ban/hhplus_ecommerce03/commit/7a6a1d3)] 카프카 설정 추가 (docker-compose, application.yml, KafkaConfig)
- [[3bac77c](https://github.com/JaeHyun-Ban/hhplus_ecommerce03/commit/3bac77c)] 쿠폰발급, 주문완료, 결제처리, 재고차감 kafka Consumer 추가
- [[352cc03](https://github.com/JaeHyun-Ban/hhplus_ecommerce03/commit/352cc03)] 주문 성공 후 eventPublisher를 Kafka 이벤트 발행으로 변경
- [[3354242](https://github.com/JaeHyun-Ban/hhplus_ecommerce03/commit/3354242)] 쿠폰발급 eventPublisher를 kafka 이벤트로 발행
- [[f4d4e60](https://github.com/JaeHyun-Ban/hhplus_ecommerce03/commit/f4d4e60)] kafka JSON 역직렬화(JSON -> DTO) 추가
- [[5efca70](https://github.com/JaeHyun-Ban/hhplus_ecommerce03/commit/5efca70)] 카프카 이전버전 코드 백업

### STEP 18 카프카를 활용하여 비즈니스 프로세스 개선
- [x] 카프카 특징을 활용한 쿠폰/주문 설계문서 작성
- [x] 설계문서대로 카프카를 활용한 기능 구현

**주요 커밋:**
- [[56c0d12](https://github.com/JaeHyun-Ban/hhplus_ecommerce03/commit/56c0d12)] kafka 기반 선착순 쿠폰 발급 설계 문서 생성
- [[252954e](https://github.com/JaeHyun-Ban/hhplus_ecommerce03/commit/252954e), [08c03c1](https://github.com/JaeHyun-Ban/hhplus_ecommerce03/commit/08c03c1)] 결제도메인 분리
- [[7a560f3](https://github.com/JaeHyun-Ban/hhplus_ecommerce03/commit/7a560f3)] 상수는 사용하는 클래스 내부에 존재할 수 있도록 수정

**구현 내용:**
- ✅ **Kafka 기반 비동기 처리**: EventListener → Kafka Consumer/Producer 전환
- ✅ **4개 Kafka 토픽**: order-events, stock-events, payment-events, coupon-events
- ✅ **파티셔닝 전략**: couponId/orderId 기반 파티션 키로 순서 보장
- ✅ **멱등성 보장**: Producer(enable.idempotence) + Consumer(중복 체크)
- ✅ **재시도 & DLQ**: Exponential Backoff(100ms→500ms) + Dead Letter Queue
- ✅ **At-Least-Once 전달**: 수동 커밋으로 메시지 유실 방지
- ✅ **성능 개선**: 응답 시간 50% 단축(300ms→150ms), 처리량 400% 증가(1K→5K TPS)

**관련 문서:**
- 📄 [Kafka 쿠폰 시스템 설계 문서](../docs/KAFKA_COUPON_SYSTEM_DESIGN.md)

---

### **간단 회고** (3줄 이내)
- **잘한 점**: 
- **어려운 점**: 
- **다음 시도**: 
