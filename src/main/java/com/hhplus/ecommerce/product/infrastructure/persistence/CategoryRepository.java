package com.hhplus.ecommerce.product.infrastructure.persistence;

import com.hhplus.ecommerce.product.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 카테고리 Repository
 *
 * Infrastructure Layer - 데이터베이스 접근 계층
 *
 * 책임:
 * - 카테고리 CRUD 연산
 * - 카테고리별 상품 조회 지원
 *
 * Use Cases:
 * - UC-003: 카테고리별 상품 조회
 * - UC-017: 카테고리 제한 쿠폰 발급
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * 카테고리명으로 조회
     *
     * Use Case:
     * - 관리자 기능: 카테고리 중복 체크
     *
     * @param name 카테고리명
     * @return 카테고리 (Optional)
     */
    Optional<Category> findByName(String name);

    /**
     * 카테고리명 존재 여부 확인
     *
     * Use Case:
     * - 관리자 기능: 카테고리 생성 시 중복 체크
     *
     * @param name 카테고리명
     * @return 존재 여부
     */
    boolean existsByName(String name);
}
