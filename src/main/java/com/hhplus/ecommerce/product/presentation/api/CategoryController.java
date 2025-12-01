package com.hhplus.ecommerce.product.presentation.api;

import com.hhplus.ecommerce.product.domain.Category;
import com.hhplus.ecommerce.product.infrastructure.persistence.CategoryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 카테고리 API 컨트롤러
 *
 * Presentation Layer - HTTP 요청/응답 처리 계층
 *
 * 책임:
 * - HTTP 요청 수신 및 응답 반환
 * - 카테고리 조회 API 제공
 *
 * Use Cases:
 * - UC-009: 카테고리 목록 조회
 */
@Tag(name = "Category API", description = "카테고리 관련 API")
@Slf4j
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;

    /**
     * 카테고리 목록 조회
     *
     * Use Case: UC-009
     * - GET /api/categories
     *
     * @return 전체 카테고리 목록
     */
    @Operation(summary = "카테고리 목록 조회", description = "전체 카테고리 목록을 조회합니다")
    @GetMapping
    public ResponseEntity<List<Category>> getCategories() {
        log.info("[API] GET /api/categories");

        List<Category> categories = categoryRepository.findAll();

        return ResponseEntity.ok(categories);
    }
}
