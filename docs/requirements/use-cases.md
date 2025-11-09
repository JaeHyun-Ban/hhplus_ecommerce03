# Use Cases (유스케이스)

> 시스템과 사용자 간의 상호작용을 단계별로 상세 기술
> - 개발자와 아키텍트를 위한 구현 가이드
> - 비즈니스 플로우의 상세한 시나리오
> - 예외 처리 및 동시성 이슈 포함

---

## 📋 목차

1. [Use Case 개요](#use-case-개요)
2. [사용자 관리](#사용자-관리)
3. [상품 관리](#상품-관리)
4. [주문 및 결제](#주문-및-결제)
5. [쿠폰 관리](#쿠폰-관리)
6. [Use Case Diagram](#use-case-diagram)

---

## Use Case 개요

### Use Case vs User Story

| 구분 | User Story | Use Case |
|------|-----------|----------|
| **목적** | 사용자 가치 전달 | 시스템 동작 상세 기술 |
| **형식** | 간단 (3줄) | 상세 (여러 단계) |
| **초점** | "왜" (Why) | "어떻게" (How) |
| **독자** | 전체 팀 | 개발자, 아키텍트 |
| **길이** | 짧음 | 길고 상세함 |

### Use Case 구조

```
Use Case ID: UC-XXX
Use Case Name: [기능명]

Primary Actor: [주요 행위자]
Secondary Actors: [보조 행위자]
Stakeholders: [이해관계자]

Preconditions: [사전 조건]
Postconditions: [사후 조건]

Main Success Scenario: [정상 플로우]
Extensions: [예외 플로우]
Alternative Flows: [대안 플로우]
```

---

## 사용자 관리

### UC-001: 잔액 충전

**Primary Actor**: 사용자
**Secondary Actors**: 결제 시스템
**Stakeholders**: 사용자, 관리자

**Preconditions**:
- 사용자가 로그인되어 있음
- 사용자 계정이 ACTIVE 상태임

**Postconditions**:
- **성공 시**: 잔액이 증가하고 이력이 기록됨
- **실패 시**: 잔액 및 이력에 변경 없음

---

#### Main Success Scenario

```
1. 사용자가 "잔액 충전" 메뉴를 클릭한다

2. 시스템이 잔액 충전 화면을 표시한다
   - 현재 잔액
   - 충전 금액 입력란

3. 사용자가 충전할 금액을 입력한다

4. 시스템이 입력값을 검증한다
   - 최소 충전 금액 (1,000원) 이상인지 확인
   - 숫자 형식이 올바른지 확인

5. 사용자가 "충전하기" 버튼을 클릭한다

6. 시스템이 다음을 수행한다 (트랜잭션 내):
   6.1. User 테이블의 balance를 증가 (비관적 락)
   6.2. BalanceHistory 생성 (타입: CHARGE)

7. 시스템이 트랜잭션을 커밋한다

8. 시스템이 충전 완료 화면을 표시한다
   - 충전 금액
   - 충전 전 잔액
   - 충전 후 잔액
   - 거래 일시

Use case 종료 (성공)
```

---

#### Extensions (예외 플로우)

```
4a. 입력한 금액이 최소 충전액(1,000원)보다 적은 경우
    4a1. 시스템이 "최소 충전 금액은 1,000원입니다" 에러 메시지를 표시한다
    4a2. Use case는 3단계로 돌아간다

4b. 입력한 값이 숫자가 아닌 경우
    4b1. 시스템이 "올바른 금액을 입력해주세요" 에러 메시지를 표시한다
    4b2. Use case는 3단계로 돌아간다

6a. 잔액 증가 중 시스템 오류 발생
    6a1. 시스템이 트랜잭션을 롤백한다
    6a2. 시스템이 "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요" 메시지를 표시한다
    6a3. 시스템이 에러 로그를 기록한다
    6a4. Use case 종료 (실패)
```

---

#### Alternative Flows

```
*a. 사용자가 언제든지 "취소" 버튼을 클릭하는 경우
    *a1. 시스템이 이전 화면으로 돌아간다
    *a2. Use case 종료 (취소)
```

---

## 상품 관리

### UC-006: 인기 상품 조회 (Top 5)

**Primary Actor**: 사용자
**Secondary Actors**: 없음
**Stakeholders**: 사용자, 마케팅팀

**Preconditions**:
- 없음 (로그인 불필요)

**Postconditions**:
- 최근 3일간 판매량 기준 상위 5개 상품 정보가 조회됨

---

#### Main Success Scenario

```
1. 사용자가 메인 페이지에 접속하거나 "인기 상품" 메뉴를 클릭한다

2. 시스템이 인기 상품 목록을 조회한다
   - ProductStatistics 테이블에서 최근 3일 데이터 조회
   - salesCount 기준 내림차순 정렬
   - 상위 5개 제한

3. 시스템이 조회된 상품 정보를 캐시에서 확인한다
   - Redis 캐시 키: "popular_products:3days"
   - TTL: 10분

4. 시스템이 인기 상품 목록을 표시한다
   각 상품별로:
   - 순위 (1~5위)
   - 상품 ID
   - 상품명
   - 가격
   - 판매 수량
   - 판매 금액

Use case 종료 (성공)
```

---

#### Extensions

```
2a. ProductStatistics 데이터가 없는 경우
    2a1. 시스템이 빈 목록을 반환한다
    2a2. 시스템이 "아직 판매 데이터가 없습니다" 메시지를 표시한다
    2a3. Use case 종료 (성공)

2b. 상품 수가 5개 미만인 경우
    2b1. 시스템이 존재하는 상품만 반환한다 (예: 3개만 있으면 3개 반환)
    2b2. 4단계로 계속 진행한다

3a. 캐시에 데이터가 없는 경우
    3a1. 시스템이 DB에서 조회한다
    3a2. 시스템이 조회 결과를 캐시에 저장한다 (TTL 10분)
    3a3. 4단계로 계속 진행한다

3b. 캐시 서버 장애 발생
    3b1. 시스템이 DB에서 직접 조회한다
    3b2. 시스템이 에러 로그를 기록한다
    3b3. 4단계로 계속 진행한다
```

---

### UC-020: 재입고 알림 신청

**Primary Actor**: 사용자
**Secondary Actors**: 알림 시스템
**Stakeholders**: 사용자, 재고 담당자

**Preconditions**:
- 사용자가 로그인되어 있음
- 조회하려는 상품이 존재함

**Postconditions**:
- **성공 시**: 재입고 알림이 등록됨
- **실패 시**: 변경 없음

---

#### Main Success Scenario

```
1. 사용자가 품절 상품의 상세 페이지를 조회한다

2. 시스템이 상품 정보를 표시한다
   - 상품명, 가격, 설명
   - 재고 상태: "품절"
   - "재입고 알림 신청" 버튼

3. 사용자가 "재입고 알림 신청" 버튼을 클릭한다

4. 시스템이 신청 가능 여부를 확인한다
   - 상품이 품절 상태(stock = 0)인지
   - 사용자가 이미 신청했는지 (PENDING 상태)

5. 시스템이 RestockNotification을 생성한다
   - user_id: 현재 사용자 ID
   - product_id: 상품 ID
   - status: PENDING
   - requested_at: 현재 시각

6. 시스템이 "재입고 시 알림을 보내드리겠습니다" 성공 메시지를 표시한다

7. 시스템이 버튼을 "알림 신청 완료"로 변경하고 비활성화한다

Use case 종료 (성공)
```

---

#### Extensions

```
4a. 상품이 품절 상태가 아닌 경우
    4a1. 시스템이 "현재 구매 가능한 상품입니다" 메시지를 표시한다
    4a2. Use case 종료 (실패)

4b. 사용자가 이미 알림을 신청한 경우 (중복 신청)
    4b1. 시스템이 "이미 알림 신청한 상품입니다" 메시지를 표시한다
    4b2. Use case 종료 (실패)

5a. DB 제약 조건 위배 (동시 요청으로 인한 중복)
    5a1. 시스템이 UniqueConstraintViolation을 처리한다
    5a2. 시스템이 "이미 알림 신청한 상품입니다" 메시지를 표시한다
    5a3. Use case 종료 (실패)
```

---

#### Related Use Case

**UC-021: 재입고 알림 발송** (시스템 자동 처리)
```
Trigger: 상품 재고가 0에서 1 이상으로 증가할 때

1. 시스템이 재고 증가를 감지한다

2. 시스템이 해당 상품의 대기 중인 알림 목록을 조회한다
   - WHERE product_id = ? AND status = 'PENDING'
   - ORDER BY requested_at ASC

3. 시스템이 각 알림에 대해:
   3.1. 알림 발송 (이메일, SMS, 푸시 등)
   3.2. status를 SENT로 변경
   3.3. sent_at을 현재 시각으로 설정

4. 시스템이 발송 완료 로그를 기록한다
```

---

## 주문 및 결제

### UC-012: 주문 생성 및 결제

**Primary Actor**: 사용자
**Secondary Actors**: 결제 시스템, 재고 시스템, 쿠폰 시스템, 외부 연동 시스템
**Stakeholders and Interests**:
- 사용자: 빠르고 정확한 결제를 원함
- 쇼핑몰: 결제 실패를 최소화하고 재고를 정확히 관리하길 원함
- 물류팀: 주문 정보를 실시간으로 받기를 원함

**Preconditions**:
- 사용자가 로그인되어 있음
- 장바구니에 1개 이상의 상품이 있음 (또는 직접 구매)
- 사용자 계정이 ACTIVE 상태임

**Postconditions**:
- **성공 시**:
  - 주문이 생성됨 (상태: PAID)
  - 각 상품의 재고가 차감됨
  - 사용자 잔액이 차감됨
  - 사용한 쿠폰이 USED 상태로 변경됨
  - 잔액 이력과 재고 이력이 기록됨
  - 외부 전송 이벤트가 생성됨 (비동기)
- **실패 시**:
  - 모든 변경사항이 롤백됨
  - 시스템 상태가 주문 시도 이전과 동일함

**Business Rules**:
- 재고 차감은 낙관적 락(@Version) 사용
- 잔액 차감은 비관적 락(SELECT FOR UPDATE) 사용
- 쿠폰은 1회만 사용 가능
- 멱등성 보장: 동일한 idempotencyKey로 중복 요청 시 기존 주문 반환

---

#### Main Success Scenario

```
1. 사용자가 장바구니 페이지에서 "주문하기" 버튼을 클릭한다
   (또는 상품 상세 페이지에서 "바로 구매" 클릭)

2. 시스템이 장바구니 내역을 조회한다
   - 각 상품의 ID, 이름, 가격, 수량
   - 장바구니에 담을 당시 가격(priceAtAdd)과 현재 가격(price) 비교

3. 시스템이 각 상품의 현재 재고를 확인한다
   - 각 상품별로 stock >= 주문 수량인지 검증

4. 시스템이 사용자의 현재 잔액을 조회한다

5. 시스템이 사용 가능한 쿠폰 목록을 표시한다
   - WHERE user_id = ? AND status = 'ISSUED'
   - AND valid_from <= NOW() AND valid_until >= NOW()

6. 사용자가 사용할 쿠폰을 선택한다 (선택 사항)

7. 시스템이 선택된 쿠폰의 유효성을 검증한다
   - 최소 주문 금액 조건 확인
   - 쿠폰 적용 가능 카테고리 확인 (있는 경우)

8. 시스템이 할인이 적용된 최종 결제 금액을 계산한다
   - 총 상품 금액 계산
   - 쿠폰 할인 금액 계산 (정액/정률)
   - 정률 쿠폰의 경우 최대 할인 금액 제한 적용
   - 최종 결제 금액 = 총 상품 금액 - 할인 금액

9. 시스템이 주문 확인 화면을 표시한다
   - 주문 상품 목록 (상품명, 가격, 수량, 소계)
   - 총 상품 금액
   - 할인 금액 (쿠폰 적용 시)
   - 최종 결제 금액
   - 현재 잔액
   - 결제 후 잔액

10. 사용자가 "결제하기" 버튼을 클릭한다

11. 시스템이 클라이언트로부터 idempotencyKey를 받는다

12. 시스템이 idempotencyKey 중복 여부를 확인한다
    - 동일한 키로 이미 처리된 주문이 있으면 해당 주문 정보 반환

13. 시스템이 다음을 원자적으로 수행한다 (트랜잭션 시작):

    13.1. 각 주문 상품에 대해 재고 차감 (낙관적 락)
          - SELECT version FROM products WHERE id = ? FOR UPDATE
          - UPDATE products SET stock = stock - ?, version = version + 1
            WHERE id = ? AND version = ?
          - 재고가 부족하거나 version 불일치 시 OptimisticLockException

    13.2. 사용자 잔액 차감 (비관적 락)
          - SELECT balance FROM users WHERE id = ? FOR UPDATE
          - 잔액 < 결제 금액인 경우 InsufficientBalanceException
          - UPDATE users SET balance = balance - ? WHERE id = ?

    13.3. 사용한 쿠폰 상태 변경 (있는 경우)
          - UPDATE user_coupons SET status = 'USED', used_at = NOW()
            WHERE id = ? AND status = 'ISSUED'
          - status가 'ISSUED'가 아니면 CouponAlreadyUsedException

    13.4. 주문 생성
          - 주문 번호 생성 (예: ORD-20251104-000001)
          - INSERT INTO orders (order_number, user_id, total_amount,
            discount_amount, final_amount, status, ordered_at,
            idempotency_key, ...)
          - status = 'PAID'

    13.5. 결제 정보 생성
          - INSERT INTO payments (order_id, amount, method, status,
            created_at, completed_at)
          - method = 'BALANCE', status = 'COMPLETED'

    13.6. 주문 항목 생성 (각 상품별)
          - INSERT INTO order_items (order_id, product_id, product_name,
            price, quantity, subtotal)
          - 주문 당시의 상품명과 가격을 스냅샷으로 저장

    13.7. 주문-쿠폰 연결 생성 (쿠폰 사용한 경우)
          - INSERT INTO order_coupons (order_id, user_coupon_id,
            discount_amount, applied_at)

    13.8. 잔액 이력 기록
          - INSERT INTO balance_histories (user_id, type, amount,
            balance_before, balance_after, description, created_at)
          - type = 'USE'

    13.9. 재고 이력 기록 (각 상품별)
          - INSERT INTO stock_histories (product_id, type, quantity,
            stock_before, stock_after, reason, created_at)
          - type = 'DECREASE', reason = '주문: [주문번호]'

14. 시스템이 트랜잭션을 커밋한다

15. 시스템이 외부 시스템 전송 이벤트를 생성한다 (비동기, 별도 트랜잭션)
    - INSERT INTO outbound_events (event_type, entity_id, payload,
      status, retry_count, max_retry_count, created_at)
    - event_type = 'ORDER_CREATED'
    - status = 'PENDING'

16. 시스템이 주문 완료 화면을 표시한다
    - 주문 번호
    - 주문 상세 정보
    - 결제 정보
    - 남은 잔액

17. 시스템이 장바구니를 비운다 (장바구니에서 주문한 경우)

Use case 종료 (성공)
```

---

#### Extensions (예외 플로우)

```
2a. 장바구니에 담을 당시 가격과 현재 가격이 다른 경우
    2a1. 시스템이 가격 변동 내역을 강조 표시한다
    2a2. 시스템이 "일부 상품의 가격이 변경되었습니다" 경고 메시지를 표시한다
    2a3. 9단계로 계속 진행한다 (사용자가 가격 변동을 인지하고 결제)

3a. 재고가 부족한 상품이 1개 이상 있는 경우
    3a1. 시스템이 재고 부족 상품 목록을 강조 표시한다
    3a2. 시스템이 "다음 상품의 재고가 부족합니다" 에러 메시지를 표시한다
         - 상품명
         - 요청 수량
         - 가능 수량
    3a3. Use case 종료 (실패)

7a. 선택한 쿠폰이 최소 주문 금액 조건을 만족하지 않는 경우
    7a1. 시스템이 "최소 주문 금액(X원)을 만족하지 않습니다" 에러 메시지를 표시한다
    7a2. Use case는 6단계로 돌아간다

7b. 선택한 쿠폰이 주문 상품의 카테고리에 적용 불가능한 경우
    7b1. 시스템이 "이 쿠폰은 해당 상품에 사용할 수 없습니다" 에러 메시지를 표시한다
    7b2. Use case는 6단계로 돌아간다

12a. 동일한 idempotencyKey로 이미 처리된 주문이 있는 경우 (중복 결제 방지)
    12a1. 시스템이 기존 주문 정보를 조회한다
    12a2. 시스템이 기존 주문 정보를 반환한다 (409 Conflict)
    12a3. Use case 종료 (멱등성 보장)

13.1a. 재고 차감 중 다른 사용자가 먼저 구매하여 재고가 부족해진 경우
    13.1a1. 낙관적 락으로 인해 OptimisticLockException 발생
    13.1a2. 시스템이 트랜잭션을 롤백한다
    13.1a3. 시스템이 "재고가 부족합니다. 다시 시도해주세요" 에러 메시지를 표시한다
            (상품명, 요청 수량, 현재 가능 수량 포함)
    13.1a4. Use case 종료 (실패)

13.1b. 재고 차감 중 버전 충돌 (동시성 문제)
    13.1b1. 시스템이 OptimisticLockException을 처리한다
    13.1b2. 시스템이 최대 3회까지 재시도한다
    13.1b3. 재시도 실패 시 13.1a3~13.1a4 실행

13.2a. 잔액이 부족한 경우
    13.2a1. 시스템이 InsufficientBalanceException 발생
    13.2a2. 시스템이 트랜잭션을 롤백한다
    13.2a3. 시스템이 "잔액이 부족합니다" 에러 메시지를 표시한다
            - 필요 금액: X원
            - 현재 잔액: Y원
            - 부족 금액: (X-Y)원
    13.2a4. Use case 종료 (실패)

13.3a. 쿠폰이 이미 사용된 경우 (동시 요청)
    13.3a1. 시스템이 CouponAlreadyUsedException 발생
    13.3a2. 시스템이 트랜잭션을 롤백한다
    13.3a3. 시스템이 "쿠폰이 이미 사용되었습니다" 에러 메시지를 표시한다
    13.3a4. Use case 종료 (실패)

13a. 트랜잭션 처리 중 시스템 오류 발생 (DB 장애, 네트워크 오류 등)
    13a1. 시스템이 모든 변경 사항을 자동 롤백한다
    13a2. 시스템이 "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요" 메시지를 표시한다
    13a3. 시스템이 에러 로그를 상세히 기록한다
         - 사용자 ID
         - 주문 시도 상품 목록
         - 에러 스택 트레이스
         - 타임스탬프
    13a4. Use case 종료 (실패)

15a. 외부 시스템 전송 이벤트 생성 실패 (비동기이므로 주문은 성공 상태 유지)
    15a1. 시스템이 에러 로그를 기록한다
    15a2. 16단계로 계속 진행한다
    15a3. 백그라운드 작업이 나중에 이벤트 재생성을 시도한다
```

---

#### Alternative Flows

```
*a. 사용자가 결제 전 언제든지 "취소" 버튼을 클릭하는 경우
    *a1. 시스템이 "주문을 취소하시겠습니까?" 확인 메시지를 표시한다
    *a2. 사용자가 "예"를 선택한다
    *a3. 시스템이 장바구니 페이지로 돌아간다
    *a4. Use case 종료 (취소)

6a. 사용자가 쿠폰 선택을 건너뛰는 경우
    6a1. 할인 없이 8단계로 진행한다

10a. 사용자가 상품 수량을 변경하는 경우
    10a1. 시스템이 2단계로 돌아간다
    10a2. 변경된 수량으로 재계산한다
```

---

#### Special Requirements

**성능 요구사항**:
- 주문 생성 트랜잭션은 2초 이내 완료
- 동시 주문 처리 가능 (분당 1000건)

**보안 요구사항**:
- idempotencyKey는 클라이언트에서 UUID v4로 생성
- 민감한 정보(잔액, 결제 금액)는 암호화 전송

**감사 요구사항**:
- 모든 주문 시도는 로그에 기록
- 실패한 주문도 별도 테이블에 기록 (분석용)

---

### UC-015: 주문 취소

**Primary Actor**: 사용자
**Secondary Actors**: 재고 시스템, 결제 시스템, 쿠폰 시스템
**Stakeholders**: 사용자, 고객 서비스팀

**Preconditions**:
- 사용자가 로그인되어 있음
- 취소하려는 주문이 존재함
- 주문 상태가 PAID임

**Postconditions**:
- **성공 시**:
  - 주문 상태가 CANCELLED로 변경됨
  - 재고가 복구됨
  - 잔액이 환불됨
  - 사용한 쿠폰이 복구됨 (ISSUED 상태로)
  - 취소 사유가 기록됨
- **실패 시**: 변경 없음

---

#### Main Success Scenario

```
1. 사용자가 주문 내역 페이지에서 특정 주문을 선택한다

2. 시스템이 주문 상세 정보를 표시한다
   - 주문 번호, 주문 상품, 결제 금액, 주문 상태

3. 사용자가 "주문 취소" 버튼을 클릭한다

4. 시스템이 취소 가능 여부를 확인한다
   - 주문 상태가 'PAID'인지
   - 주문 소유자가 현재 사용자인지

5. 시스템이 취소 사유 입력 팝업을 표시한다

6. 사용자가 취소 사유를 선택하거나 입력한다
   - 단순 변심
   - 상품 오류
   - 배송 지연
   - 기타 (직접 입력)

7. 사용자가 "취소 확정" 버튼을 클릭한다

8. 시스템이 다음을 원자적으로 수행한다 (트랜잭션 시작):

   8.1. 주문 상태 변경
        - UPDATE orders SET status = 'CANCELLED',
          cancelled_at = NOW(), cancellation_reason = ?
          WHERE id = ? AND status = 'PAID'

   8.2. 재고 복구 (각 주문 상품별)
        - UPDATE products SET stock = stock + ? WHERE id = ?
        - INSERT INTO stock_histories (product_id, type, quantity,
          stock_before, stock_after, reason, created_at)
        - type = 'INCREASE', reason = '주문 취소: [주문번호]'

   8.3. 잔액 환불
        - UPDATE users SET balance = balance + ? WHERE id = ?
        - INSERT INTO balance_histories (user_id, type, amount,
          balance_before, balance_after, description, created_at)
        - type = 'REFUND', description = '주문 취소 환불: [주문번호]'

   8.4. 쿠폰 복구 (사용한 쿠폰이 있는 경우)
        - UPDATE user_coupons SET status = 'ISSUED', used_at = NULL
          WHERE id = ? AND status = 'USED'

   8.5. 결제 상태 변경
        - UPDATE payments SET status = 'CANCELLED'
          WHERE order_id = ?

9. 시스템이 트랜잭션을 커밋한다

10. 시스템이 외부 시스템 전송 이벤트를 생성한다 (비동기)
    - event_type = 'ORDER_CANCELLED'

11. 시스템이 취소 완료 화면을 표시한다
    - "주문이 취소되었습니다"
    - 환불 금액
    - 환불 후 잔액

Use case 종료 (성공)
```

---

#### Extensions

```
4a. 주문 상태가 'PAID'가 아닌 경우
    4a1. 시스템이 "취소할 수 없는 주문입니다" 에러 메시지를 표시한다
         (예: 이미 취소됨, 이미 환불됨)
    4a2. Use case 종료 (실패)

4b. 주문 소유자가 현재 사용자가 아닌 경우 (권한 오류)
    4b1. 시스템이 403 Forbidden 에러를 반환한다
    4b2. Use case 종료 (실패)

8a. 트랜잭션 처리 중 시스템 오류 발생
    8a1. 시스템이 모든 변경 사항을 롤백한다
    8a2. 시스템이 "일시적인 오류가 발생했습니다" 에러 메시지를 표시한다
    8a3. Use case 종료 (실패)

8.4a. 쿠폰이 이미 만료된 경우
    8.4a1. 쿠폰은 복구하지 않고 그대로 USED 상태 유지
    8.4a2. 시스템이 "사용한 쿠폰은 이미 만료되어 복구되지 않습니다" 안내 메시지 추가
    8.4a3. 나머지 단계는 정상 진행

10a. 외부 시스템 전송 이벤트 생성 실패
    10a1. 시스템이 에러 로그를 기록한다
    10a2. 11단계로 계속 진행한다
```

---

## 쿠폰 관리

### UC-017: 쿠폰 발급 (선착순)

**Primary Actor**: 사용자
**Secondary Actors**: 없음
**Stakeholders**: 사용자, 마케팅팀

**Preconditions**:
- 사용자가 로그인되어 있음
- 발급하려는 쿠폰이 존재함
- 쿠폰이 ACTIVE 상태임
- 쿠폰 발급 기간 내임

**Postconditions**:
- **성공 시**:
  - 사용자에게 쿠폰이 발급됨 (UserCoupon 생성)
  - 쿠폰의 issuedQuantity가 1 증가
  - totalQuantity와 동일해지면 쿠폰 상태가 EXHAUSTED로 변경
- **실패 시**: 변경 없음

---

#### Main Success Scenario

```
1. 사용자가 쿠폰 목록 페이지를 조회한다

2. 시스템이 현재 발급 가능한 쿠폰 목록을 표시한다
   - WHERE status = 'ACTIVE'
   - AND issue_start_at <= NOW() AND issue_end_at >= NOW()
   각 쿠폰별로:
   - 쿠폰 코드 (WELCOME2025)
   - 쿠폰명 (신규 가입 쿠폰)
   - 할인 타입 (정액/정률)
   - 할인 값 (10% 또는 5,000원)
   - 최소 주문 금액 (10,000원 이상)
   - 남은 수량 (850/1000)
   - 유효 기간 (2025.10.01 ~ 2025.12.31)

3. 사용자가 원하는 쿠폰의 "발급받기" 버튼을 클릭한다

4. 시스템이 쿠폰 발급 가능 여부를 확인한다 (선행 검증):
   4.1. 쿠폰이 ACTIVE 상태인지
   4.2. 현재 시각이 발급 기간 내인지
        (issue_start_at <= NOW() <= issue_end_at)
   4.3. 남은 수량이 있는지
        (issued_quantity < total_quantity)
   4.4. 사용자가 이미 발급받았는지
        (동일 coupon_id, user_id로 UserCoupon 존재 여부)
   4.5. 1인당 최대 발급 수를 초과하지 않았는지
        (해당 쿠폰의 사용자 발급 횟수 < max_issue_per_user)

5. 시스템이 다음을 원자적으로 수행한다 (트랜잭션 시작, 비관적 락):

   5.1. Coupon 테이블의 발급 수량 증가 (SELECT FOR UPDATE)
        - SELECT id, issued_quantity, total_quantity, status
          FROM coupons WHERE id = ? FOR UPDATE
        - 락을 획득하여 동시 발급 방지
        - issued_quantity < total_quantity 재확인
        - UPDATE coupons SET issued_quantity = issued_quantity + 1
          WHERE id = ?

   5.2. 발급 수량 확인 및 상태 변경
        - IF issued_quantity + 1 >= total_quantity THEN
            UPDATE coupons SET status = 'EXHAUSTED' WHERE id = ?

   5.3. UserCoupon 생성
        - INSERT INTO user_coupons (user_id, coupon_id, status,
          issued_at)
        - status = 'ISSUED'
        - issued_at = NOW()

6. 시스템이 트랜잭션을 커밋한다

7. 시스템이 "쿠폰이 발급되었습니다" 성공 메시지를 표시한다
   - 쿠폰명
   - 할인 내용
   - 사용 가능 기간

8. 시스템이 발급된 쿠폰을 사용자의 쿠폰 목록에 추가한다

Use case 종료 (성공)
```

---

#### Extensions

```
4.1a. 쿠폰이 비활성 상태(INACTIVE 또는 EXHAUSTED)인 경우
    4.1a1. 시스템이 "이 쿠폰은 현재 발급할 수 없습니다" 에러 메시지를 표시한다
    4.1a2. Use case 종료 (실패)

4.2a. 쿠폰 발급 기간이 아닌 경우
    4.2a1. 시스템이 "쿠폰 발급 기간이 아닙니다" 에러 메시지를 표시한다
           - 발급 시작: YYYY-MM-DD HH:MM
           - 발급 종료: YYYY-MM-DD HH:MM
    4.2a2. Use case 종료 (실패)

4.3a. 쿠폰 수량이 모두 소진된 경우
    4.3a1. 시스템이 "쿠폰이 모두 소진되었습니다" 에러 메시지를 표시한다
    4.3a2. Use case 종료 (실패)

4.4a. 사용자가 이미 해당 쿠폰을 발급받은 경우 (중복 발급 방지)
    4.4a1. 시스템이 "이미 발급받은 쿠폰입니다" 에러 메시지를 표시한다
    4.4a2. 이미 발급받은 쿠폰 정보를 표시한다
           - 발급일시
           - 사용 가능 기간
    4.4a3. Use case 종료 (실패)

4.5a. 1인당 최대 발급 수량을 초과한 경우
    4.5a1. 시스템이 "최대 발급 수량(X장)을 초과했습니다" 에러 메시지를 표시한다
    4.5a2. Use case 종료 (실패)

5.1a. 동시에 여러 사용자가 마지막 쿠폰을 발급받으려는 경우 (경합 조건)
    5.1a1. 비관적 락(SELECT FOR UPDATE)으로 인해 하나씩 순차 처리됨
    5.1a2. 먼저 락을 획득한 사용자만 발급 진행
    5.1a3. 나중에 락을 획득한 사용자는 issued_quantity 재확인에서 실패
    5.1a4. 시스템이 트랜잭션을 롤백한다
    5.1a5. 시스템이 "쿠폰이 모두 소진되었습니다" 에러 메시지를 표시한다
    5.1a6. Use case 종료 (실패)

5.3a. UserCoupon 생성 시 Unique Constraint 위배 (동시 요청으로 인한 중복)
    5.3a1. 시스템이 UniqueConstraintViolationException 처리
    5.3a2. 시스템이 트랜잭션을 롤백한다
    5.3a3. 시스템이 "이미 발급받은 쿠폰입니다" 에러 메시지를 표시한다
    5.3a4. Use case 종료 (실패)

5a. 트랜잭션 처리 중 시스템 오류 발생
    5a1. 시스템이 모든 변경 사항을 롤백한다
    5a2. 시스템이 "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요" 메시지를 표시한다
    5a3. 시스템이 에러 로그를 기록한다
    5a4. Use case 종료 (실패)
```

---

#### Special Requirements

**동시성 제어**:
- 선착순 쿠폰 발급은 비관적 락(SELECT FOR UPDATE) 사용
- Redis 카운터를 활용한 빠른 재고 확인 (선택적)
  - Redis에 남은 수량 캐싱
  - 발급 가능 여부 빠른 확인
  - DB는 최종 정합성 보장용

**성능 요구사항**:
- 쿠폰 발급은 1초 이내 완료
- 동시 발급 요청 처리 (초당 1000건)

**감사 요구사항**:
- 쿠폰 발급 실패 이력도 로그에 기록
- 소진 시각 기록 (마케팅 분석용)

---

## Use Case Diagram

### 전체 시스템 Use Case Diagram

```
                    E-Commerce System
     ┌─────────────────────────────────────────┐
     │                                         │
     │   ┌─────────────────────────┐          │
     │   │   사용자 관리            │          │
     │   │                         │          │
     │   │  UC-001: 잔액 충전      │◄─────────┼─── 사용자
     │   │  UC-002: 잔액 조회      │          │
     │   │  UC-003: 이력 조회      │          │
     │   └─────────────────────────┘          │
     │                                         │
     │   ┌─────────────────────────┐          │
     │   │   상품 관리              │          │
     │   │                         │          │
     │   │  UC-004: 상품 목록      │◄─────────┼─── 사용자
     │   │  UC-005: 상품 상세      │          │
     │   │  UC-006: 인기 상품      │          │
     │   │  UC-020: 재입고 알림    │          │
     │   └─────────────────────────┘          │
     │            │includes                   │
     │            ▼                            │
     │   ┌─────────────────┐                  │
     │   │  재고 확인      │                  │
     │   └─────────────────┘                  │
     │                                         │
     │   ┌─────────────────────────┐          │
     │   │   주문 및 결제           │          │
     │   │                         │          │
     │   │  UC-012: 주문 생성      │◄─────────┼─── 사용자
     │   │  UC-013: 주문 조회      │          │
     │   │  UC-015: 주문 취소      │          │
     │   └──────────┬──────────────┘          │
     │              │includes                 │
     │              ▼                          │
     │   ┌─────────────────┐                  │
     │   │  재고 차감      │                  │
     │   │  잔액 차감      │                  │
     │   └─────────────────┘                  │
     │              │extends                  │
     │              ▼                          │
     │   ┌─────────────────┐                  │
     │   │  쿠폰 적용      │                  │
     │   └─────────────────┘                  │
     │                                         │
     │   ┌─────────────────────────┐          │
     │   │   쿠폰 관리              │          │
     │   │                         │          │
     │   │  UC-016: 쿠폰 조회      │◄─────────┼─── 사용자
     │   │  UC-017: 쿠폰 발급      │          │
     │   │  UC-018: 보유 쿠폰      │          │
     │   └─────────────────────────┘          │
     │                                         │
     │   ┌─────────────────────────┐          │
     │   │   외부 연동              │          │
     │   │                         │          │
     │   │  UC-999: 이벤트 전송    │◄─────────┼─── 시스템
     │   └─────────────────────────┘          │    (비동기)
     │                                         │
     └─────────────────────────────────────────┘

                     │
                     │ 연동
                     ▼
          ┌──────────────────┐
          │  외부 시스템      │
          │  (물류, 알림 등)  │
          └──────────────────┘
```

---

### 주문 생성 상세 Use Case Diagram

```
        사용자
          │
          │ initiates
          ▼
   ┌──────────────────┐
   │  UC-012          │
   │  주문 생성       │
   └────┬─────────────┘
        │
        │ includes
        ├───────────────┐
        │               │
        ▼               ▼
   ┌─────────┐    ┌─────────┐
   │재고 확인│    │잔액 확인│
   └─────────┘    └─────────┘
        │
        │ extends (선택)
        ▼
   ┌─────────┐
   │쿠폰 적용│
   └─────────┘
        │
        │ triggers
        ▼
   ┌──────────────┐
   │외부 시스템   │
   │이벤트 전송   │
   └──────────────┘
```

---

## 📝 Use Case 작성 가이드라인

### 1. 단계 작성 규칙
- **주어는 행위자**: "사용자가", "시스템이"
- **사용자 목표 관점**: "데이터베이스에 저장한다" (X) → "주문을 생성한다" (O)
- **UI 세부사항 배제**: "빨간색 버튼" (X) → "결제하기 버튼" (O)

### 2. 예외 처리 규칙
- 모든 가능한 실패 시나리오 문서화
- 각 예외마다 시스템 반응 명시
- 트랜잭션 롤백 명시

### 3. 트랜잭션 경계 명시
```
X. 시스템이 다음을 원자적으로 수행한다 (트랜잭션 시작):
   X.1. ...
   X.2. ...
```

### 4. 동시성 이슈 고려
- 낙관적 락 vs 비관적 락 명시
- 경합 조건(Race Condition) 시나리오 포함

---

## 사용자 관리 (추가)

### UC-002: 사용자 등록

**Primary Actor**: 사용자
**Secondary Actors**: 이메일 서비스
**Stakeholders**: 사용자, 관리자

**Preconditions**:
- 없음 (신규 사용자)

**Postconditions**:
- **성공 시**: 사용자 계정이 생성되고 ACTIVE 상태로 설정됨, 초기 잔액 0원
- **실패 시**: 변경 없음

---

#### Main Success Scenario

```
1. 사용자가 회원가입 페이지에 접속한다

2. 시스템이 회원가입 폼을 표시한다
   - 이메일 입력란
   - 비밀번호 입력란
   - 비밀번호 확인 입력란
   - 이름 입력란

3. 사용자가 정보를 입력한다
   - 이메일: user@example.com
   - 비밀번호: password123
   - 이름: 홍길동

4. 시스템이 입력값을 검증한다
   - 이메일 형식 확인
   - 비밀번호 길이 (최소 8자) 확인
   - 이름 길이 (2~50자) 확인

5. 사용자가 "가입하기" 버튼을 클릭한다

6. 시스템이 이메일 중복 여부를 확인한다

7. 시스템이 사용자를 생성한다 (트랜잭션):
   7.1. 비밀번호를 암호화한다
   7.2. User 엔티티를 생성한다
       - email, password (암호화됨), name
       - balance = 0
       - role = USER
       - status = ACTIVE
       - createdAt = 현재 시각

8. 시스템이 트랜잭션을 커밋한다

9. 시스템이 회원가입 완료 화면을 표시한다
   - 사용자 ID
   - 이메일
   - 이름

Use case 종료 (성공)
```

---

#### Extensions

```
4a. 이메일 형식이 올바르지 않은 경우
    4a1. 시스템이 "올바른 이메일 형식을 입력해주세요" 에러 메시지를 표시한다
    4a2. Use case는 3단계로 돌아간다

4b. 비밀번호가 8자 미만인 경우
    4b1. 시스템이 "비밀번호는 최소 8자 이상이어야 합니다" 에러 메시지를 표시한다
    4b2. Use case는 3단계로 돌아간다

4c. 이름이 2자 미만 또는 50자 초과인 경우
    4c1. 시스템이 "이름은 2~50자 사이여야 합니다" 에러 메시지를 표시한다
    4c2. Use case는 3단계로 돌아간다

6a. 이미 등록된 이메일인 경우
    6a1. 시스템이 "이미 사용 중인 이메일입니다" 에러 메시지를 표시한다
    6a2. Use case 종료 (실패, 409 Conflict)

7a. 트랜잭션 처리 중 시스템 오류 발생
    7a1. 시스템이 트랜잭션을 롤백한다
    7a2. 시스템이 "일시적인 오류가 발생했습니다" 메시지를 표시한다
    7a3. Use case 종료 (실패)
```

---

### UC-003: 사용자 조회

**Primary Actor**: 사용자, 관리자
**Secondary Actors**: 없음
**Stakeholders**: 사용자, 관리자

**Preconditions**:
- 사용자가 로그인되어 있음

**Postconditions**:
- 사용자 정보가 조회됨

---

#### Main Success Scenario

```
1. 사용자가 마이페이지 또는 프로필 메뉴를 클릭한다

2. 시스템이 사용자 ID로 사용자 정보를 조회한다
   - SELECT * FROM users WHERE id = ?

3. 시스템이 사용자 정보를 표시한다
   - 사용자 ID
   - 이메일
   - 이름
   - 현재 잔액
   - 역할 (USER/ADMIN)
   - 상태 (ACTIVE/INACTIVE/DELETED)
   - 가입일시

Use case 종료 (성공)
```

---

#### Extensions

```
2a. 사용자를 찾을 수 없는 경우
    2a1. 시스템이 "사용자를 찾을 수 없습니다" 에러 메시지를 표시한다
    2a2. Use case 종료 (실패, 404 Not Found)

2b. 사용자 상태가 DELETED인 경우
    2b1. 시스템이 "탈퇴한 사용자입니다" 에러 메시지를 표시한다
    2b2. Use case 종료 (실패, 404 Not Found)
```

---

### UC-004: 잔액 조회

**Primary Actor**: 사용자
**Secondary Actors**: 없음
**Stakeholders**: 사용자

**Preconditions**:
- 사용자가 로그인되어 있음

**Postconditions**:
- 현재 잔액이 조회됨

---

#### Main Success Scenario

```
1. 사용자가 "내 잔액" 또는 "포인트" 메뉴를 클릭한다

2. 시스템이 사용자의 현재 잔액을 조회한다
   - SELECT balance FROM users WHERE id = ?

3. 시스템이 잔액 정보를 표시한다
   - 사용자 ID
   - 현재 잔액 (원 단위)

Use case 종료 (성공)
```

---

#### Extensions

```
2a. 사용자를 찾을 수 없는 경우
    2a1. 시스템이 "사용자를 찾을 수 없습니다" 에러 메시지를 표시한다
    2a2. Use case 종료 (실패, 404 Not Found)
```

---

### UC-005: 잔액 이력 조회

**Primary Actor**: 사용자
**Secondary Actors**: 없음
**Stakeholders**: 사용자

**Preconditions**:
- 사용자가 로그인되어 있음

**Postconditions**:
- 잔액 변동 이력이 조회됨

---

#### Main Success Scenario

```
1. 사용자가 "잔액 내역" 또는 "포인트 내역" 메뉴를 클릭한다

2. 시스템이 잔액 변동 이력을 조회한다 (페이징)
   - SELECT * FROM balance_histories
     WHERE user_id = ?
     ORDER BY created_at DESC
     LIMIT ? OFFSET ?

3. 시스템이 이력 목록을 표시한다
   각 이력별로:
   - 거래 ID
   - 거래 유형 (CHARGE/USE/REFUND)
   - 거래 금액
   - 거래 전 잔액
   - 거래 후 잔액
   - 설명
   - 거래 일시

4. 시스템이 페이징 정보를 표시한다
   - 현재 페이지
   - 페이지 크기
   - 전체 항목 수
   - 전체 페이지 수

Use case 종료 (성공)
```

---

#### Extensions

```
2a. 이력이 없는 경우
    2a1. 시스템이 빈 목록을 반환한다
    2a2. 시스템이 "아직 거래 내역이 없습니다" 메시지를 표시한다
    2a3. Use case 종료 (성공)
```

---

## 상품 관리 (추가)

### UC-007: 상품 목록 조회

**Primary Actor**: 사용자
**Secondary Actors**: 없음
**Stakeholders**: 사용자, 마케팅팀

**Preconditions**:
- 없음 (로그인 불필요)

**Postconditions**:
- 상품 목록이 조회됨

---

#### Main Success Scenario

```
1. 사용자가 상품 목록 페이지에 접속한다

2. 사용자가 카테고리를 선택한다 (선택 사항)

3. 시스템이 상품 목록을 조회한다 (페이징)
   - 카테고리 필터가 있는 경우:
     SELECT * FROM products
     WHERE category_id = ? AND status = 'AVAILABLE'
     ORDER BY created_at DESC
     LIMIT ? OFFSET ?
   - 카테고리 필터가 없는 경우:
     SELECT * FROM products
     WHERE status = 'AVAILABLE'
     ORDER BY created_at DESC
     LIMIT ? OFFSET ?

4. 시스템이 상품 목록을 표시한다
   각 상품별로:
   - 상품 ID
   - 상품명
   - 가격
   - 재고 수량
   - 카테고리명
   - 상태 (AVAILABLE/OUT_OF_STOCK/DISCONTINUED)

5. 시스템이 페이징 정보를 표시한다
   - 현재 페이지
   - 페이지 크기
   - 전체 상품 수
   - 전체 페이지 수

Use case 종료 (성공)
```

---

#### Extensions

```
3a. 조회된 상품이 없는 경우
    3a1. 시스템이 빈 목록을 반환한다
    3a2. 시스템이 "상품이 없습니다" 메시지를 표시한다
    3a3. Use case 종료 (성공)

3b. 유효하지 않은 카테고리 ID인 경우
    3b1. 시스템이 "존재하지 않는 카테고리입니다" 에러 메시지를 표시한다
    3b2. Use case 종료 (실패)
```

---

### UC-008: 상품 상세 조회

**Primary Actor**: 사용자
**Secondary Actors**: 없음
**Stakeholders**: 사용자

**Preconditions**:
- 없음 (로그인 불필요)

**Postconditions**:
- 상품 상세 정보가 조회됨

---

#### Main Success Scenario

```
1. 사용자가 상품 목록에서 특정 상품을 클릭한다

2. 시스템이 상품 상세 정보를 조회한다
   - SELECT * FROM products WHERE id = ?
   - JOIN categories ON products.category_id = categories.id

3. 시스템이 상품 상세 정보를 표시한다
   - 상품 ID
   - 상품명
   - 설명
   - 가격
   - 현재 재고 수량
   - 안전 재고 수준
   - 카테고리 ID
   - 카테고리명
   - 상태 (AVAILABLE/OUT_OF_STOCK/DISCONTINUED)
   - 등록일시
   - 수정일시

4. 재고 상태에 따라 버튼을 표시한다
   - stock > 0: "장바구니 담기" 버튼 활성화
   - stock = 0: "품절" 표시 및 "재입고 알림 신청" 버튼

Use case 종료 (성공)
```

---

#### Extensions

```
2a. 상품을 찾을 수 없는 경우
    2a1. 시스템이 "상품을 찾을 수 없습니다" 에러 메시지를 표시한다
    2a2. Use case 종료 (실패, 404 Not Found)

2b. 상품 상태가 DISCONTINUED인 경우
    2b1. 시스템이 "단종된 상품입니다" 메시지를 표시한다
    2b2. "장바구니 담기" 버튼을 비활성화한다
    2b3. 3단계로 계속 진행한다
```

---

### UC-009: 카테고리 목록 조회

**Primary Actor**: 사용자
**Secondary Actors**: 없음
**Stakeholders**: 사용자, 관리자

**Preconditions**:
- 없음

**Postconditions**:
- 카테고리 목록이 조회됨

---

#### Main Success Scenario

```
1. 사용자가 상품 페이지에 접속한다

2. 시스템이 모든 카테고리를 조회한다
   - SELECT * FROM categories ORDER BY name ASC

3. 시스템이 카테고리 목록을 표시한다 (사이드바 또는 필터)
   각 카테고리별로:
   - 카테고리 ID
   - 카테고리명
   - 설명

Use case 종료 (성공)
```

---

#### Extensions

```
2a. 카테고리가 없는 경우
    2a1. 시스템이 빈 목록을 반환한다
    2a2. Use case 종료 (성공)
```

---

## 장바구니 관리

### UC-010: 장바구니 조회

**Primary Actor**: 사용자
**Secondary Actors**: 없음
**Stakeholders**: 사용자

**Preconditions**:
- 사용자가 로그인되어 있음

**Postconditions**:
- 장바구니 내역이 조회됨

---

#### Main Success Scenario

```
1. 사용자가 "장바구니" 메뉴를 클릭한다

2. 시스템이 사용자의 장바구니를 조회한다
   - SELECT * FROM carts WHERE user_id = ?
   - JOIN cart_items ON carts.id = cart_items.cart_id
   - JOIN products ON cart_items.product_id = products.id

3. 시스템이 장바구니 정보를 표시한다
   - 장바구니 ID
   - 사용자 ID
   - 장바구니 항목 목록:
     각 항목별로:
     - 항목 ID
     - 상품 ID
     - 상품명
     - 현재 가격
     - 담을 당시 가격 (priceAtAdd)
     - 수량
     - 소계 (현재 가격 × 수량)
     - 가격 변동 여부 (isPriceChanged)
   - 총 금액

4. 가격이 변경된 항목이 있는 경우
   4.1. 시스템이 해당 항목을 강조 표시한다
   4.2. 시스템이 "일부 상품의 가격이 변경되었습니다" 알림을 표시한다

Use case 종료 (성공)
```

---

#### Extensions

```
2a. 장바구니가 비어있는 경우
    2a1. 시스템이 빈 장바구니를 표시한다
    2a2. 시스템이 "장바구니가 비어있습니다" 메시지를 표시한다
    2a3. Use case 종료 (성공)

2b. 장바구니에 품절된 상품이 있는 경우
    2b1. 시스템이 해당 항목을 비활성화한다
    2b2. 시스템이 "품절" 표시를 한다
    2b3. 3단계로 계속 진행한다
```

---

### UC-011: 장바구니에 상품 추가

**Primary Actor**: 사용자
**Secondary Actors**: 없음
**Stakeholders**: 사용자

**Preconditions**:
- 사용자가 로그인되어 있음
- 추가하려는 상품이 존재함

**Postconditions**:
- **성공 시**: 장바구니에 상품이 추가됨
- **실패 시**: 변경 없음

---

#### Main Success Scenario

```
1. 사용자가 상품 상세 페이지에서 수량을 선택한다

2. 사용자가 "장바구니 담기" 버튼을 클릭한다

3. 시스템이 상품 정보를 조회한다
   - SELECT * FROM products WHERE id = ?

4. 시스템이 재고를 확인한다
   - stock >= 요청 수량

5. 시스템이 사용자의 장바구니를 조회하거나 생성한다
   - SELECT * FROM carts WHERE user_id = ?
   - 없으면 INSERT INTO carts (user_id, created_at)

6. 시스템이 장바구니에 동일 상품이 있는지 확인한다

7. 시스템이 장바구니 항목을 추가하거나 업데이트한다 (트랜잭션):
   7.1. 동일 상품이 없는 경우:
        INSERT INTO cart_items (cart_id, product_id, quantity,
                                price_at_add, created_at)
   7.2. 동일 상품이 있는 경우:
        UPDATE cart_items
        SET quantity = quantity + ?, price_at_add = ?, updated_at = NOW()
        WHERE id = ?

8. 시스템이 트랜잭션을 커밋한다

9. 시스템이 "장바구니에 담았습니다" 성공 메시지를 표시한다
   - 장바구니 항목 정보 반환

Use case 종료 (성공)
```

---

#### Extensions

```
3a. 상품을 찾을 수 없는 경우
    3a1. 시스템이 "상품을 찾을 수 없습니다" 에러 메시지를 표시한다
    3a2. Use case 종료 (실패, 404 Not Found)

4a. 재고가 부족한 경우
    4a1. 시스템이 "재고가 부족합니다" 에러 메시지를 표시한다
         - 요청 수량: X개
         - 가능 수량: Y개
    4a2. Use case 종료 (실패, 400 Bad Request)

4b. 상품이 품절(stock = 0)인 경우
    4b1. 시스템이 "품절된 상품입니다" 에러 메시지를 표시한다
    4b2. Use case 종료 (실패)

7a. 트랜잭션 처리 중 시스템 오류 발생
    7a1. 시스템이 트랜잭션을 롤백한다
    7a2. 시스템이 "일시적인 오류가 발생했습니다" 메시지를 표시한다
    7a3. Use case 종료 (실패)
```

---

### UC-013: 장바구니 상품 수량 변경

**Primary Actor**: 사용자
**Secondary Actors**: 없음
**Stakeholders**: 사용자

**Preconditions**:
- 사용자가 로그인되어 있음
- 장바구니에 변경하려는 상품이 있음

**Postconditions**:
- **성공 시**: 장바구니 항목의 수량이 변경됨
- **실패 시**: 변경 없음

---

#### Main Success Scenario

```
1. 사용자가 장바구니 페이지에서 특정 상품의 수량을 변경한다

2. 시스템이 장바구니 항목을 조회한다
   - SELECT * FROM cart_items WHERE id = ?

3. 시스템이 상품 정보를 조회한다
   - SELECT * FROM products WHERE id = ?

4. 시스템이 재고를 확인한다
   - stock >= 변경할 수량

5. 시스템이 장바구니 항목을 업데이트한다 (트랜잭션):
   - UPDATE cart_items
     SET quantity = ?, updated_at = NOW()
     WHERE id = ?

6. 시스템이 트랜잭션을 커밋한다

7. 시스템이 업데이트된 장바구니 항목을 표시한다
   - 상품 ID
   - 상품명
   - 가격
   - 변경된 수량
   - 소계 (가격 × 변경된 수량)

Use case 종료 (성공)
```

---

#### Extensions

```
2a. 장바구니 항목을 찾을 수 없는 경우
    2a1. 시스템이 "장바구니 항목을 찾을 수 없습니다" 에러 메시지를 표시한다
    2a2. Use case 종료 (실패, 404 Not Found)

4a. 재고가 부족한 경우
    4a1. 시스템이 "재고가 부족합니다" 에러 메시지를 표시한다
         - 요청 수량: X개
         - 가능 수량: Y개
    4a2. Use case 종료 (실패, 400 Bad Request)

5a. 트랜잭션 처리 중 시스템 오류 발생
    5a1. 시스템이 트랜잭션을 롤백한다
    5a2. 시스템이 "일시적인 오류가 발생했습니다" 메시지를 표시한다
    5a3. Use case 종료 (실패)
```

---

### UC-014: 장바구니 상품 삭제

**Primary Actor**: 사용자
**Secondary Actors**: 없음
**Stakeholders**: 사용자

**Preconditions**:
- 사용자가 로그인되어 있음
- 장바구니에 삭제하려는 상품이 있음

**Postconditions**:
- **성공 시**: 장바구니 항목이 삭제됨
- **실패 시**: 변경 없음

---

#### Main Success Scenario

```
1. 사용자가 장바구니 페이지에서 특정 상품의 "삭제" 버튼을 클릭한다

2. 시스템이 장바구니 항목을 조회한다
   - SELECT * FROM cart_items WHERE id = ?

3. 시스템이 장바구니 항목을 삭제한다 (트랜잭션):
   - DELETE FROM cart_items WHERE id = ?

4. 시스템이 트랜잭션을 커밋한다

5. 시스템이 "상품이 삭제되었습니다" 성공 메시지를 표시한다

6. 시스템이 업데이트된 장바구니를 표시한다

Use case 종료 (성공)
```

---

#### Extensions

```
2a. 장바구니 항목을 찾을 수 없는 경우
    2a1. 시스템이 "장바구니 항목을 찾을 수 없습니다" 에러 메시지를 표시한다
    2a2. Use case 종료 (실패, 404 Not Found)

3a. 트랜잭션 처리 중 시스템 오류 발생
    3a1. 시스템이 트랜잭션을 롤백한다
    3a2. 시스템이 "일시적인 오류가 발생했습니다" 메시지를 표시한다
    3a3. Use case 종료 (실패)
```

---

### UC-016: 장바구니 비우기

**Primary Actor**: 사용자
**Secondary Actors**: 없음
**Stakeholders**: 사용자

**Preconditions**:
- 사용자가 로그인되어 있음

**Postconditions**:
- **성공 시**: 장바구니의 모든 항목이 삭제됨
- **실패 시**: 변경 없음

---

#### Main Success Scenario

```
1. 사용자가 장바구니 페이지에서 "전체 삭제" 버튼을 클릭한다

2. 시스템이 확인 메시지를 표시한다
   - "장바구니를 비우시겠습니까?"

3. 사용자가 "확인"을 클릭한다

4. 시스템이 사용자의 장바구니를 조회한다
   - SELECT * FROM carts WHERE user_id = ?

5. 시스템이 장바구니의 모든 항목을 삭제한다 (트랜잭션):
   - DELETE FROM cart_items WHERE cart_id = ?

6. 시스템이 트랜잭션을 커밋한다

7. 시스템이 "장바구니가 비워졌습니다" 성공 메시지를 표시한다

8. 시스템이 빈 장바구니를 표시한다

Use case 종료 (성공)
```

---

#### Extensions

```
3a. 사용자가 "취소"를 클릭한 경우
    3a1. Use case 종료 (취소)

4a. 장바구니가 없는 경우
    4a1. 시스템이 "장바구니가 이미 비어있습니다" 메시지를 표시한다
    4a2. Use case 종료 (성공)

5a. 트랜잭션 처리 중 시스템 오류 발생
    5a1. 시스템이 트랜잭션을 롤백한다
    5a2. 시스템이 "일시적인 오류가 발생했습니다" 메시지를 표시한다
    5a3. Use case 종료 (실패)
```

---

## 주문 및 결제 (추가)

### UC-018: 주문 목록 조회

**Primary Actor**: 사용자
**Secondary Actors**: 없음
**Stakeholders**: 사용자

**Preconditions**:
- 사용자가 로그인되어 있음

**Postconditions**:
- 주문 목록이 조회됨

---

#### Main Success Scenario

```
1. 사용자가 "주문 내역" 메뉴를 클릭한다

2. 시스템이 사용자의 주문 목록을 조회한다 (페이징)
   - SELECT * FROM orders
     WHERE user_id = ?
     ORDER BY ordered_at DESC
     LIMIT ? OFFSET ?

3. 시스템이 주문 목록을 표시한다
   각 주문별로:
   - 주문 ID
   - 주문 번호 (예: ORD-20251104-000001)
   - 총 상품 금액
   - 할인 금액
   - 최종 결제 금액
   - 주문 상태 (PENDING/PAID/CANCELLED/REFUNDED)
   - 주문 일시

4. 시스템이 페이징 정보를 표시한다
   - 현재 페이지
   - 페이지 크기
   - 전체 주문 수
   - 전체 페이지 수

Use case 종료 (성공)
```

---

#### Extensions

```
2a. 주문 내역이 없는 경우
    2a1. 시스템이 빈 목록을 반환한다
    2a2. 시스템이 "아직 주문 내역이 없습니다" 메시지를 표시한다
    2a3. Use case 종료 (성공)
```

---

### UC-019: 주문 상세 조회

**Primary Actor**: 사용자
**Secondary Actors**: 없음
**Stakeholders**: 사용자

**Preconditions**:
- 사용자가 로그인되어 있음
- 조회하려는 주문이 존재함

**Postconditions**:
- 주문 상세 정보가 조회됨

---

#### Main Success Scenario

```
1. 사용자가 주문 목록에서 특정 주문을 클릭한다

2. 시스템이 주문 상세 정보를 조회한다
   - SELECT * FROM orders WHERE id = ?
   - JOIN order_items ON orders.id = order_items.order_id
   - JOIN payments ON orders.id = payments.order_id
   - LEFT JOIN order_coupons ON orders.id = order_coupons.order_id

3. 시스템이 주문 상세 정보를 표시한다
   - 주문 ID
   - 주문 번호
   - 사용자 ID
   - 사용자 이름
   - 주문 상품 목록:
     각 상품별로:
     - 상품 ID
     - 상품명 (주문 당시)
     - 가격 (주문 당시)
     - 수량
     - 소계
   - 총 상품 금액
   - 할인 금액
   - 최종 결제 금액
   - 사용한 쿠폰 정보 (있는 경우):
     - 쿠폰 ID
     - 쿠폰명
     - 할인 금액
   - 결제 정보:
     - 결제 ID
     - 결제 수단 (BALANCE)
     - 결제 금액
     - 결제 상태 (PENDING/COMPLETED/FAILED/CANCELLED)
   - 주문 상태 (PENDING/PAID/CANCELLED/REFUNDED)
   - 주문 일시
   - 결제 일시
   - 취소 일시 (취소된 경우)
   - 취소 사유 (취소된 경우)

Use case 종료 (성공)
```

---

#### Extensions

```
2a. 주문을 찾을 수 없는 경우
    2a1. 시스템이 "주문을 찾을 수 없습니다" 에러 메시지를 표시한다
    2a2. Use case 종료 (실패, 404 Not Found)

2b. 주문 소유자가 현재 사용자가 아닌 경우 (권한 오류)
    2b1. 시스템이 403 Forbidden 에러를 반환한다
    2b2. Use case 종료 (실패)
```

---

## 쿠폰 관리 (추가)

### UC-021: 쿠폰 목록 조회

**Primary Actor**: 사용자
**Secondary Actors**: 없음
**Stakeholders**: 사용자, 마케팅팀

**Preconditions**:
- 없음 (로그인 불필요)

**Postconditions**:
- 발급 가능한 쿠폰 목록이 조회됨

---

#### Main Success Scenario

```
1. 사용자가 "쿠폰" 메뉴를 클릭한다

2. 시스템이 현재 발급 가능한 쿠폰 목록을 조회한다 (페이징)
   - SELECT * FROM coupons
     WHERE status = 'ACTIVE'
     AND issue_start_at <= NOW()
     AND issue_end_at >= NOW()
     ORDER BY issue_end_at ASC
     LIMIT ? OFFSET ?

3. 시스템이 쿠폰 목록을 표시한다
   각 쿠폰별로:
   - 쿠폰 ID
   - 쿠폰 코드 (예: WELCOME2025)
   - 쿠폰명 (예: 신규 가입 쿠폰)
   - 설명
   - 할인 타입 (FIXED_AMOUNT/PERCENTAGE)
   - 할인 값 (10% 또는 5,000원)
   - 최소 주문 금액
   - 최대 할인 금액 (정률 쿠폰의 경우)
   - 전체 수량
   - 남은 수량
   - 쿠폰 상태 (ACTIVE/INACTIVE/EXHAUSTED)
   - 발급 시작일
   - 발급 종료일
   - 사용 가능 기간 (validFrom ~ validUntil)

4. 시스템이 페이징 정보를 표시한다
   - 현재 페이지
   - 페이지 크기
   - 전체 쿠폰 수
   - 전체 페이지 수

Use case 종료 (성공)
```

---

#### Extensions

```
2a. 발급 가능한 쿠폰이 없는 경우
    2a1. 시스템이 빈 목록을 반환한다
    2a2. 시스템이 "현재 발급 가능한 쿠폰이 없습니다" 메시지를 표시한다
    2a3. Use case 종료 (성공)
```

---

### UC-022: 보유 쿠폰 조회

**Primary Actor**: 사용자
**Secondary Actors**: 없음
**Stakeholders**: 사용자

**Preconditions**:
- 사용자가 로그인되어 있음

**Postconditions**:
- 사용자가 보유한 쿠폰 목록이 조회됨

---

#### Main Success Scenario

```
1. 사용자가 "내 쿠폰" 메뉴를 클릭한다

2. 사용자가 필터를 선택한다 (선택 사항)
   - 전체 보기
   - 사용 가능한 쿠폰만
   - 사용 완료된 쿠폰만
   - 만료된 쿠폰만

3. 시스템이 사용자의 쿠폰 목록을 조회한다
   - 필터가 있는 경우:
     SELECT * FROM user_coupons
     WHERE user_id = ? AND status = ?
     ORDER BY issued_at DESC
   - 필터가 없는 경우:
     SELECT * FROM user_coupons
     WHERE user_id = ?
     ORDER BY issued_at DESC

4. 각 쿠폰에 대해 사용 가능 여부를 계산한다
   - status = 'ISSUED'
   - AND validFrom <= NOW()
   - AND validUntil >= NOW()

5. 시스템이 쿠폰 목록을 표시한다
   각 쿠폰별로:
   - 사용자 쿠폰 ID
   - 사용자 ID
   - 쿠폰 정보:
     - 쿠폰 ID
     - 쿠폰 코드
     - 쿠폰명
     - 할인 타입 (FIXED_AMOUNT/PERCENTAGE)
     - 할인 값
     - 최소 주문 금액
     - 최대 할인 금액
   - 쿠폰 상태 (ISSUED/USED/EXPIRED/REVOKED)
   - 발급일시
   - 사용 가능 여부 (canUse: true/false)

Use case 종료 (성공)
```

---

#### Extensions

```
3a. 보유한 쿠폰이 없는 경우
    3a1. 시스템이 빈 목록을 반환한다
    3a2. 시스템이 "보유한 쿠폰이 없습니다" 메시지를 표시한다
    3a3. Use case 종료 (성공)

3b. 필터링 결과가 없는 경우
    3b1. 시스템이 빈 목록을 반환한다
    3b2. 시스템이 "해당하는 쿠폰이 없습니다" 메시지를 표시한다
    3b3. Use case 종료 (성공)
```

---

**최종 업데이트**: 2025-11-07
**문서 버전**: 2.0.0
**추가된 UseCase**: UC-002 ~ UC-022 (16개 추가)