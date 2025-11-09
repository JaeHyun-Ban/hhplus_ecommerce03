package com.hhplus.ecommerce.common;

import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.FluentQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Fake Repository 공통 베이스 클래스
 * JpaRepository의 모든 필수 메서드 기본 구현 제공
 */
public abstract class FakeRepositorySupport<T, ID> implements JpaRepository<T, ID> {

    @Override
    public void flush() {}

    @Override
    public <S extends T> S saveAndFlush(S entity) {
        return save(entity);
    }

    @Override
    public <S extends T> List<S> saveAllAndFlush(Iterable<S> entities) {
        return saveAll(entities);
    }

    @Override
    public void deleteAllInBatch(Iterable<T> entities) {
        entities.forEach(this::delete);
    }

    @Override
    public void deleteAllByIdInBatch(Iterable<ID> ids) {
        ids.forEach(this::deleteById);
    }

    @Override
    public void deleteAllInBatch() {
        deleteAll();
    }

    @Override
    @Deprecated
    public T getOne(ID id) {
        return findById(id).orElse(null);
    }

    @Override
    @Deprecated
    public T getById(ID id) {
        return findById(id).orElse(null);
    }

    @Override
    public T getReferenceById(ID id) {
        return findById(id).orElseThrow();
    }

    @Override
    public <S extends T> List<S> findAll(Example<S> example) {
        return new ArrayList<>();
    }

    @Override
    public <S extends T> List<S> findAll(Example<S> example, Sort sort) {
        return new ArrayList<>();
    }

    @Override
    public <S extends T> List<S> saveAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        entities.forEach(e -> result.add(save(e)));
        return result;
    }

    @Override
    public long count() {
        return findAll().size();
    }

    @Override
    public void deleteAllById(Iterable<? extends ID> ids) {
        ids.forEach(this::deleteById);
    }

    @Override
    public void deleteAll(Iterable<? extends T> entities) {
        entities.forEach(this::delete);
    }

    @Override
    public <S extends T> Page<S> findAll(Example<S> example, Pageable pageable) {
        return Page.empty();
    }

    @Override
    public <S extends T> long count(Example<S> example) {
        return 0;
    }

    @Override
    public <S extends T> boolean exists(Example<S> example) {
        return false;
    }

    @Override
    public <S extends T, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
        return null;
    }

    @Override
    public List<T> findAll(Sort sort) {
        return findAll();
    }

    @Override
    public Page<T> findAll(Pageable pageable) {
        return Page.empty();
    }

    @Override
    public <S extends T> Optional<S> findOne(Example<S> example) {
        return Optional.empty();
    }
}
