package com.hhplus.ecommerce.product.presentation.api;

import com.hhplus.ecommerce.product.application.ProductService;
import com.hhplus.ecommerce.product.domain.Product;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 상품 API 컨트롤러
 *
 * Presentation Layer - HTTP 요청/응답 처리 계층
 *
 * 책임:
 * - HTTP 요청 수신 및 응답 반환
 * - 상품 조회 API 제공
 *
 * Use Cases:
 * - UC-003: 상품 목록 조회
 * - UC-004: 상품 상세 조회
 * - UC-006: 인기 상품 조회
 */
@Tag(name = "Product API", description = "상품 관련 API")
@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * 상품 목록 조회
     *
     * Use Case: UC-003
     * - GET /api/products
     *
     * @param pageable 페이징 정보 (기본: page=0, size=20)
     * @return 상품 페이지
     */
    @Operation(summary = "상품 목록 조회", description = "판매 가능한 상품 목록을 조회합니다")
    @GetMapping
    public ResponseEntity<Page<Product>> getProducts(
            @PageableDefault(size = 20) Pageable pageable) {

        log.info("[API] GET /api/products - page: {}, size: {}",
                 pageable.getPageNumber(), pageable.getPageSize());

        Page<Product> products = productService.getAvailableProducts(pageable);

        return ResponseEntity.ok(products);
    }

    /**
     * 카테고리별 상품 목록 조회
     *
     * Use Case: UC-003 (변형)
     * - GET /api/products?categoryId={categoryId}
     *
     * @param categoryId 카테고리 ID
     * @param pageable 페이징 정보
     * @return 상품 페이지
     */
    @Operation(summary = "카테고리별 상품 조회", description = "특정 카테고리의 상품 목록을 조회합니다")
    @GetMapping(params = "categoryId")
    public ResponseEntity<Page<Product>> getProductsByCategory(
            @RequestParam Long categoryId,
            @PageableDefault(size = 20) Pageable pageable) {

        log.info("[API] GET /api/products?categoryId={}", categoryId);

        Page<Product> products = productService.getProductsByCategory(categoryId, pageable);

        return ResponseEntity.ok(products);
    }

    /**
     * 상품 상세 조회
     *
     * Use Case: UC-004
     * - GET /api/products/{productId}
     *
     * @param productId 상품 ID
     * @return 상품 상세 정보
     */
    @Operation(summary = "상품 상세 조회", description = "상품의 상세 정보를 조회합니다")
    @GetMapping("/{productId}")
    public ResponseEntity<Product> getProduct(@PathVariable Long productId) {
        log.info("[API] GET /api/products/{}", productId);

        Product product = productService.getProduct(productId);

        return ResponseEntity.ok(product);
    }

    /**
     * 인기 상품 조회
     *
     * Use Case: UC-006
     * - GET /api/products/popular
     * - 최근 3일 판매량 기준 TOP 5
     *
     * @return 인기 상품 목록 (최대 5개)
     */
    @Operation(
        summary = "인기 상품 조회",
        description = "최근 3일 판매량 기준 인기 상품 TOP 5를 조회합니다"
    )
    @GetMapping("/popular")
    public ResponseEntity<List<Product>> getPopularProducts() {
        log.info("[API] GET /api/products/popular");

        List<Product> products = productService.getPopularProducts();

        return ResponseEntity.ok(products);
    }
}
