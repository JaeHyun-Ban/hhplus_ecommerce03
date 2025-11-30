package com.hhplus.ecommerce.application.product;

import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductStatistics;
import com.hhplus.ecommerce.infrastructure.persistence.product.ProductRepository;
import com.hhplus.ecommerce.infrastructure.persistence.product.ProductStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 상품 애플리케이션 서비스
 *
 * Application Layer - Use Case 실행 계층
 *
 * 책임:
 * - UC-003: 상품 목록 조회
 * - UC-004: 상품 상세 조회
 * - UC-006: 인기 상품 조회
 *
 * 레이어 의존성:
 * - Infrastructure Layer: ProductRepository, ProductStatisticsRepository
 * - Domain Layer: Product, ProductStatistics
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductStatisticsRepository productStatisticsRepository;

    /**
     * 상품 목록 조회
     *
     * Use Case: UC-003
     * - 판매 가능한 상품만 조회
     * - 페이징 지원
     *
     * 필터:
     * - status = AVAILABLE
     * - stock > 0
     *
     * @param pageable 페이징 정보
     * @return 상품 페이지
     */
    public Page<Product> getAvailableProducts(Pageable pageable) {
        log.info("[UC-003] 상품 목록 조회 - page: {}, size: {}",
                 pageable.getPageNumber(), pageable.getPageSize());

        return productRepository.findAvailableProducts(pageable);
    }

    /**
     * 카테고리별 상품 목록 조회
     *
     * Use Case: UC-003 (변형)
     *
     * @param categoryId 카테고리 ID
     * @param pageable 페이징 정보
     * @return 상품 페이지
     */
    public Page<Product> getProductsByCategory(Long categoryId, Pageable pageable) {
        log.info("[UC-003] 카테고리별 상품 조회 - categoryId: {}", categoryId);

        return productRepository.findByCategoryId(categoryId, pageable);
    }

    /**
     * 상품 상세 조회
     *
     * Use Case: UC-004
     * - 상품 정보 조회
     *
     * @param productId 상품 ID
     * @return 상품 상세 정보
     */
    public Product getProduct(Long productId) {
        log.info("[UC-004] 상품 상세 조회 - productId: {}", productId);

        return productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다"));
    }

    /**
     * 인기 상품 조회 (최근 3일 판매량 기준 TOP 5)
     *
     * Use Case: UC-006
     *
     * 집계 기준:
     * - 최근 3일간 판매 통계
     * - 판매량(salesCount) 합계 기준
     * - 상위 5개
     *
     * @return 인기 상품 목록 (최대 5개)
     */
    public List<Product> getPopularProducts() {
        log.info("[UC-006] 인기 상품 조회 - 최근 3일 판매량 기준");

        // 최근 3일 날짜 범위
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(2); // 오늘 포함 3일

        // 인기 상품 ID 조회
        List<Long> topProductIds = productStatisticsRepository
            .findTopProductIdsByDateRange(startDate, endDate, 5);

        // 상품 정보 조회
        if (topProductIds.isEmpty()) {
            log.info("[UC-006] 통계 데이터 없음, 기본 목록 반환");
            // 통계 데이터가 없으면 최신 상품 5개 반환
            return productRepository.findAvailableProducts(
                org.springframework.data.domain.PageRequest.of(0, 5)
            ).getContent();
        }

        // ID 순서를 유지하며 상품 조회
        List<Product> products = productRepository.findAllById(topProductIds);

        // 원래 순서 유지 (판매량 순)
        return topProductIds.stream()
            .map(id -> products.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElse(null))
            .filter(p -> p != null)
            .collect(Collectors.toList());
    }
}
