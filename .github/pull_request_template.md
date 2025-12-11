## [STEP 15-16] 반재현(e-commerce)

---
### STEP 15 Application Event
- [x] 주문/예약 정보를 원 트랜잭션이 종료된 이후에 전송
- [x] 주문/예약 정보를 전달하는 부가 로직에 대한 관심사를 메인 서비스에서 분리

**주요 커밋:**
- [[ff1925e](https://github.com/JaeHyun-Ban/hhplus_ecommerce03/commit/ff1925e)] 주문 로직을 이벤트리스너를 활용, 각 도메인별로 비동기적인 실행이되도록 변경
- [[7129c33](https://github.com/JaeHyun-Ban/hhplus_ecommerce03/commit/7129c33)] 각 도메인별 이벤트의 페이로드 추가
- [[e75a845](https://github.com/JaeHyun-Ban/hhplus_ecommerce03/commit/e75a845)] 쿠폰발급 이벤트 비동기처리 추가
- [[f2308c2](https://github.com/JaeHyun-Ban/hhplus_ecommerce03/commit/f2308c2)] 각 도메인별 테스트 업데이트 및 추가

### STEP 16 Transaction Diagnosis
- [x] 도메인별로 트랜잭션이 분리되었을 때 발생 가능한 문제 파악
- [x] 트랜잭션이 분리되더라도 데이터 일관성을 보장할 수 있는 분산 트랜잭션 설계

**주요 커밋:**
- [[7c823c9](https://github.com/JaeHyun-Ban/hhplus_ecommerce03/commit/7c823c9)] 분산 트랜잭션 설계문서 추가
- [[f441329](https://github.com/JaeHyun-Ban/hhplus_ecommerce03/commit/f441329)] 도메인별 비동기 이벤트 실패를 대비한 이벤트 소싱 추가
- [[fbeab8d](https://github.com/JaeHyun-Ban/hhplus_ecommerce03/commit/fbeab8d)] orderEntity 결제 컬럼 추가 및 결제 레포지토리 생성
- [[31649d4](https://github.com/JaeHyun-Ban/hhplus_ecommerce03/commit/31649d4)] 하드코딩된 값을 상수로 대체

**구현 내용:**
- ✅ **Saga 패턴 (Choreography)**: 비동기 이벤트 기반 분산 트랜잭션 구현
- ✅ **이벤트 소싱**: DomainEventStore를 통한 실패 이벤트 추적 및 자동 재시도
- ✅ **보상 트랜잭션**: 실패 시 자동 롤백으로 데이터 일관성 보장
- ✅ **트랜잭션 분리**: `@TransactionalEventListener` + `REQUIRES_NEW`
- ✅ **Payment 엔티티**: 결제 정보 독립 관리
- ✅ **성능 개선**: 응답 시간 67% 단축, 처리량 4배 증가

**관련 문서:**
- 📄 [분산 트랜잭션 설계 문서](../docs/DISTRIBUTED_TRANSACTION_DESIGN.md)
- 📄 [README.md - 분산 트랜잭션 섹션](../README.md#-분산-트랜잭션)

---

### **간단 회고** (3줄 이내)
- **잘한 점**:
- **어려운 점**:
- **다음 시도**: