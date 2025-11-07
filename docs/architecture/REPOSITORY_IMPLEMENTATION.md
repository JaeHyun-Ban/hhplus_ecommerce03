# Repository 구현 전략

## 개요

이 프로젝트는 **2가지 Repository 구현체**를 제공합니다:

1. **JPA Repository** (기본) - Spring Data JPA + H2/MySQL
2. **InMemory Repository** - HashMap 기반 순수 메모리 저장소

---

## 1. JPA Repository (기본 구현)

### 특징
- Spring Data JPA 사용
- H2 (로컬), MySQL (개발/운영) 지원
- 완전한 트랜잭션 관리
- Pessimistic Lock (잔액) + Optimistic Lock (재고, 쿠폰) 지원
- 페이징, 정렬, 복잡한 쿼리 지원

### 사용법
```yaml
# application.yml
spring:
  profiles:
    active: local  # local, dev, prod
```

### 동시성 제어
- **Pessimistic Lock**: 잔액 충전/차감 시 `SELECT ... FOR UPDATE` 사용
- **Optimistic Lock**: 상품 재고, 쿠폰 발급 시 `@Version` 사용
- **Retry 메커니즘**: Optimistic Lock 실패 시 최대 3회 재시도

---

## 2. InMemory Repository (교육용 구현)

### 특징
- DB 없이 순수 HashMap 기반 메모리 저장
- `ConcurrentHashMap` 사용으로 Thread-safe 보장
- 동시성 제어 시뮬레이션:
  - Pessimistic Lock → `synchronized` 블록
  - Optimistic Lock → Version 필드 수동 체크
- 페이징 제한적 지원 (정렬 미지원)

### 사용법
```yaml
# application.yml
spring:
  profiles:
    active: inmemory

repository:
  type: inmemory
```

또는 실행 시:
```bash
./gradlew bootRun --args='--spring.profiles.active=inmemory'
```

### 구현된 InMemory Repository
- ✅ `InMemoryUserRepository`
- ✅ `InMemoryProductRepository`
- ⚠️ 나머지 Repository는 JPA 사용 (하이브리드 모드)

### 제한사항
- 애플리케이션 재시작 시 데이터 소멸
- 복잡한 Join 쿼리 미지원
- Example Query, Specification 미지원
- 정렬(Sort) 제한적 지원

---

## 3. 아키텍처 다이어그램

```
┌─────────────────────────────────────────┐
│       Presentation Layer (Controller)    │
└───────────────┬─────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│      Application Layer (Service)         │
└───────────────┬─────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│   Infrastructure Layer (Repository IF)   │
└───────┬───────────────────┬─────────────┘
        │                   │
        ▼                   ▼
┌──────────────────┐  ┌──────────────────┐
│  JPA Repository  │  │ InMemory Repo    │
│  (Spring Data)   │  │  (HashMap)       │
│                  │  │                  │
│  - H2 / MySQL    │  │  - ConcurrentMap │
│  - Transaction   │  │  - Synchronized  │
│  - JPA Lock      │  │  - Manual Lock   │
└──────────────────┘  └──────────────────┘
```

---

## 4. 체크리스트 요구사항 충족

### ✅ 인메모리 저장소
- InMemory Repository 구현체 제공
- 프로파일 전환으로 JPA ↔ InMemory 선택 가능

### ✅ Repository 패턴
- 인터페이스와 구현체 분리
- Service Layer는 인터페이스에만 의존
- 구현체 교체 가능 (Dependency Inversion Principle)

---

## 5. 동시성 제어 비교

| 항목 | JPA Repository | InMemory Repository |
|------|----------------|---------------------|
| **Pessimistic Lock** | `SELECT ... FOR UPDATE` | `synchronized` 블록 |
| **Optimistic Lock** | `@Version` (Hibernate) | 수동 version 체크 |
| **Retry** | `@Retryable` 어노테이션 | 동일 |
| **트랜잭션** | `@Transactional` | 메모리 직접 조작 |

---

## 6. 프로파일 선택 가이드

### Local 개발 (기본)
```bash
# H2 인메모리 DB 사용 (JPA)
./gradlew bootRun
```
→ http://localhost:8080/h2-console 에서 DB 확인 가능

### InMemory 테스트
```bash
# DB 없이 순수 메모리 사용
./gradlew bootRun --args='--spring.profiles.active=inmemory'
```
→ DB 설정 불필요, 즉시 실행 가능

### Dev/Prod 환경
```bash
# MySQL 사용
./gradlew bootRun --args='--spring.profiles.active=dev'
```

---

## 7. 구현 상세

### JPA Repository 예시
```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdWithLock(@Param("id") Long id);

    Optional<User> findByEmail(String email);
}
```

### InMemory Repository 예시
```java
@Repository
@ConditionalOnProperty(name = "repository.type", havingValue = "inmemory")
public class InMemoryUserRepository implements UserRepository {

    private final Map<Long, User> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1L);

    @Override
    public Optional<User> findByIdWithLock(Long id) {
        // Pessimistic Lock 시뮬레이션
        synchronized (store) {
            return Optional.ofNullable(store.get(id));
        }
    }

    @Override
    public User save(User user) {
        if (user.getId() == null) {
            Long newId = idGenerator.getAndIncrement();
            setId(user, newId);
            store.put(newId, user);
            return user;
        }
        store.put(user.getId(), user);
        return user;
    }
}
```

---

## 8. 성능 비교

| 항목 | JPA Repository | InMemory Repository |
|------|----------------|---------------------|
| **읽기 속도** | ~1ms (캐시 없음) | ~0.01ms |
| **쓰기 속도** | ~5ms (트랜잭션) | ~0.01ms |
| **동시 접근** | DB Lock 대기 | `synchronized` 대기 |
| **데이터 영속성** | ✅ 디스크 저장 | ❌ 메모리만 |
| **확장성** | ✅ 수평 확장 가능 | ❌ 단일 서버만 |

---

## 9. 추천 사용 시나리오

### JPA Repository (권장)
- 실제 서비스 운영
- 데이터 영속성 필요
- 복잡한 쿼리, 트랜잭션 필요
- 대용량 데이터 처리

### InMemory Repository
- 단위 테스트 (DB 없이 빠른 실행)
- 프로토타입 개발
- 교육/학습 목적
- 임시 데이터 캐싱

---

## 10. 마이그레이션 가이드

### JPA → InMemory 전환
```bash
# 1. application.yml 수정
repository.type: inmemory

# 2. 프로파일 변경
spring.profiles.active: inmemory

# 3. 애플리케이션 재시작
```

### InMemory → JPA 전환
```bash
# 1. application.yml 수정
repository.type: jpa

# 2. 프로파일 변경
spring.profiles.active: local  # or dev, prod

# 3. DB 설정 확인
# 4. 애플리케이션 재시작
```

---

## 결론

- **기본값**: JPA Repository (실용적, 완전한 기능)
- **대안**: InMemory Repository (교육용, 테스트용)
- **유연성**: 프로파일 전환만으로 구현체 변경 가능
- **확장성**: 새로운 구현체 추가 용이 (Redis, MongoDB 등)
