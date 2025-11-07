package com.hhplus.ecommerce.infrastructure.persistence.inmemory;

import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.infrastructure.persistence.user.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * User 인메모리 Repository 구현체
 *
 * Infrastructure Layer - 데이터 저장소 계층
 *
 * 특징:
 * - HashMap 기반 메모리 저장
 * - Thread-safe (ConcurrentHashMap 사용)
 * - Pessimistic Lock 시뮬레이션 (synchronized)
 *
 * 사용 조건:
 * - application.yml에 repository.type=inmemory 설정 시 활성화
 */
@Repository
@ConditionalOnProperty(name = "repository.type", havingValue = "inmemory")
public class InMemoryUserRepository implements UserRepository {

    private final Map<Long, User> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1L);

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<User> findByIdWithLock(Long id) {
        // Pessimistic Lock 시뮬레이션 - 실제로는 synchronized로 동시 접근 제어
        synchronized (store) {
            return Optional.ofNullable(store.get(id));
        }
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return store.values().stream()
                .filter(user -> user.getEmail().equals(email))
                .findFirst();
    }

    @Override
    public boolean existsByEmail(String email) {
        return store.values().stream()
                .anyMatch(user -> user.getEmail().equals(email));
    }

    @Override
    public User save(User user) {
        if (user.getId() == null) {
            // 새 엔티티 저장
            Long newId = idGenerator.getAndIncrement();
            User newUser = User.builder()
                    .email(user.getEmail())
                    .password(user.getPassword())
                    .name(user.getName())
                    .balance(user.getBalance())
                    .role(user.getRole())
                    .status(user.getStatus())
                    .build();

            // Reflection으로 ID 설정 (실제로는 Builder에 id 추가 필요)
            setId(newUser, newId);
            store.put(newId, newUser);
            return newUser;
        } else {
            // 기존 엔티티 업데이트
            store.put(user.getId(), user);
            return user;
        }
    }

    @Override
    public void delete(User user) {
        store.remove(user.getId());
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    @Override
    public List<User> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public boolean existsById(Long id) {
        return store.containsKey(id);
    }

    @Override
    public long count() {
        return store.size();
    }

    @Override
    public void flush() {
        // In-memory에서는 불필요
    }

    @Override
    public <S extends User> S saveAndFlush(S entity) {
        return save(entity);
    }

    @Override
    public <S extends User> List<S> saveAllAndFlush(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        entities.forEach(entity -> result.add(save(entity)));
        return result;
    }

    @Override
    public void deleteAllInBatch(Iterable<User> entities) {
        entities.forEach(this::delete);
    }

    @Override
    public void deleteAllByIdInBatch(Iterable<Long> ids) {
        ids.forEach(this::deleteById);
    }

    @Override
    public void deleteAllInBatch() {
        store.clear();
    }

    @Override
    public User getOne(Long id) {
        return findById(id).orElse(null);
    }

    @Override
    public User getById(Long id) {
        return findById(id).orElseThrow();
    }

    @Override
    public User getReferenceById(Long id) {
        return getById(id);
    }

    @Override
    public <S extends User> List<S> saveAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        entities.forEach(entity -> result.add(save(entity)));
        return result;
    }

    @Override
    public List<User> findAllById(Iterable<Long> ids) {
        List<User> result = new ArrayList<>();
        ids.forEach(id -> findById(id).ifPresent(result::add));
        return result;
    }

    @Override
    public void deleteAll(Iterable<? extends User> entities) {
        entities.forEach(this::delete);
    }

    @Override
    public void deleteAll() {
        store.clear();
    }

    // Unsupported operations (페이징, 정렬, Example 등)
    @Override
    public List<User> findAll(Sort sort) {
        throw new UnsupportedOperationException("Sort not supported in InMemory implementation");
    }

    @Override
    public Page<User> findAll(Pageable pageable) {
        throw new UnsupportedOperationException("Pageable not supported in InMemory implementation");
    }

    @Override
    public <S extends User> Optional<S> findOne(Example<S> example) {
        throw new UnsupportedOperationException("Example not supported in InMemory implementation");
    }

    @Override
    public <S extends User> List<S> findAll(Example<S> example) {
        throw new UnsupportedOperationException("Example not supported in InMemory implementation");
    }

    @Override
    public <S extends User> List<S> findAll(Example<S> example, Sort sort) {
        throw new UnsupportedOperationException("Example not supported in InMemory implementation");
    }

    @Override
    public <S extends User> Page<S> findAll(Example<S> example, Pageable pageable) {
        throw new UnsupportedOperationException("Example not supported in InMemory implementation");
    }

    @Override
    public <S extends User> long count(Example<S> example) {
        throw new UnsupportedOperationException("Example not supported in InMemory implementation");
    }

    @Override
    public <S extends User> boolean exists(Example<S> example) {
        throw new UnsupportedOperationException("Example not supported in InMemory implementation");
    }

    @Override
    public <S extends User, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
        throw new UnsupportedOperationException("FluentQuery not supported in InMemory implementation");
    }

    /**
     * Reflection을 사용하여 ID 설정
     */
    private void setId(User user, Long id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set ID", e);
        }
    }
}
