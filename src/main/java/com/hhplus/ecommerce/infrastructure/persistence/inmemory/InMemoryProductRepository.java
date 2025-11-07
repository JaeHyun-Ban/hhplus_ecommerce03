package com.hhplus.ecommerce.infrastructure.persistence.inmemory;

import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductStatus;
import com.hhplus.ecommerce.infrastructure.persistence.product.ProductRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Product 인메모리 Repository 구현체
 *
 * Infrastructure Layer - 데이터 저장소 계층
 *
 * 특징:
 * - ConcurrentHashMap 기반 메모리 저장
 * - Optimistic Lock 시뮬레이션 (version 체크)
 * - 페이징 지원
 */
@Repository
@ConditionalOnProperty(name = "repository.type", havingValue = "inmemory")
public class InMemoryProductRepository implements ProductRepository {

    private final Map<Long, Product> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1L);

    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Product> findByIdWithLock(Long id) {
        // Optimistic Lock 시뮬레이션
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Page<Product> findAvailableProducts(Pageable pageable) {
        List<Product> available = store.values().stream()
                .filter(p -> p.getStatus() == ProductStatus.AVAILABLE)
                .collect(Collectors.toList());

        return createPage(available, pageable);
    }

    @Override
    public Page<Product> findByCategoryId(Long categoryId, Pageable pageable) {
        List<Product> filtered = store.values().stream()
                .filter(p -> p.getCategory() != null && p.getCategory().getId().equals(categoryId))
                .filter(p -> p.getStatus() == ProductStatus.AVAILABLE)
                .collect(Collectors.toList());

        return createPage(filtered, pageable);
    }

    @Override
    public List<Product> findByIdIn(List<Long> ids) {
        return ids.stream()
                .map(store::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public Product save(Product product) {
        if (product.getId() == null) {
            Long newId = idGenerator.getAndIncrement();
            setId(product, newId);
            store.put(newId, product);
            return product;
        } else {
            // Optimistic Lock 체크
            Product existing = store.get(product.getId());
            if (existing != null && existing.getVersion() != null) {
                if (!existing.getVersion().equals(product.getVersion())) {
                    throw new RuntimeException("Optimistic lock exception");
                }
                // Version 증가
                incrementVersion(product);
            }
            store.put(product.getId(), product);
            return product;
        }
    }

    @Override
    public void delete(Product product) {
        store.remove(product.getId());
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public Page<Product> findAll(Pageable pageable) {
        return createPage(new ArrayList<>(store.values()), pageable);
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
    public <S extends Product> S saveAndFlush(S entity) {
        return save(entity);
    }

    @Override
    public <S extends Product> List<S> saveAllAndFlush(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        entities.forEach(entity -> result.add(save(entity)));
        return result;
    }

    @Override
    public void deleteAllInBatch(Iterable<Product> entities) {
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
    public Product getOne(Long id) {
        return findById(id).orElse(null);
    }

    @Override
    public Product getById(Long id) {
        return findById(id).orElseThrow();
    }

    @Override
    public Product getReferenceById(Long id) {
        return getById(id);
    }

    @Override
    public <S extends Product> List<S> saveAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        entities.forEach(entity -> result.add(save(entity)));
        return result;
    }

    @Override
    public List<Product> findAllById(Iterable<Long> ids) {
        List<Product> result = new ArrayList<>();
        ids.forEach(id -> findById(id).ifPresent(result::add));
        return result;
    }

    @Override
    public void deleteAll(Iterable<? extends Product> entities) {
        entities.forEach(this::delete);
    }

    @Override
    public void deleteAll() {
        store.clear();
    }

    // Unsupported operations
    @Override
    public List<Product> findAll(Sort sort) {
        throw new UnsupportedOperationException("Sort not fully supported in InMemory implementation");
    }

    @Override
    public <S extends Product> Optional<S> findOne(Example<S> example) {
        throw new UnsupportedOperationException("Example not supported");
    }

    @Override
    public <S extends Product> List<S> findAll(Example<S> example) {
        throw new UnsupportedOperationException("Example not supported");
    }

    @Override
    public <S extends Product> List<S> findAll(Example<S> example, Sort sort) {
        throw new UnsupportedOperationException("Example not supported");
    }

    @Override
    public <S extends Product> Page<S> findAll(Example<S> example, Pageable pageable) {
        throw new UnsupportedOperationException("Example not supported");
    }

    @Override
    public <S extends Product> long count(Example<S> example) {
        throw new UnsupportedOperationException("Example not supported");
    }

    @Override
    public <S extends Product> boolean exists(Example<S> example) {
        throw new UnsupportedOperationException("Example not supported");
    }

    @Override
    public <S extends Product, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
        throw new UnsupportedOperationException("FluentQuery not supported");
    }

    /**
     * 페이징 헬퍼 메서드
     */
    private Page<Product> createPage(List<Product> list, Pageable pageable) {
        int total = list.size();
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), total);

        List<Product> pageContent = (start >= total) ? Collections.emptyList() : list.subList(start, end);
        return new PageImpl<>(pageContent, pageable, total);
    }

    /**
     * Reflection으로 ID 설정
     */
    private void setId(Product product, Long id) {
        try {
            var field = Product.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(product, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set ID", e);
        }
    }

    /**
     * Version 증가
     */
    private void incrementVersion(Product product) {
        try {
            var field = Product.class.getDeclaredField("version");
            field.setAccessible(true);
            Long currentVersion = (Long) field.get(product);
            field.set(product, currentVersion == null ? 1L : currentVersion + 1);
        } catch (Exception e) {
            // Version 필드가 없거나 접근 불가면 무시
        }
    }
}
