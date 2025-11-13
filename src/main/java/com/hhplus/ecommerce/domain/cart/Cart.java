package com.hhplus.ecommerce.domain.cart;

import com.hhplus.ecommerce.domain.common.BaseEntity;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "carts", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Cart extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    // 비즈니스 로직: 상품 추가
    public CartItem addItem(Product product, Integer quantity) {
        // 이미 존재하는 상품인지 확인
        CartItem existingItem = findItemByProduct(product);
        if (existingItem != null) {
            existingItem.updateQuantity(existingItem.getQuantity() + quantity);
            return existingItem;
        }

        // 새로운 아이템 추가
        CartItem newItem = CartItem.builder()
                .cart(this)
                .product(product)
                .quantity(quantity)
                .priceAtAdd(product.getPrice())
                .build();
        this.items.add(newItem);
        return newItem;
    }

    // 비즈니스 로직: 상품 제거
    public void removeItem(Long productId) {
        this.items.removeIf(item -> item.getProduct().getId().equals(productId));
    }

    // 비즈니스 로직: 상품 수량 변경
    public void updateItemQuantity(Long productId, Integer quantity) {
        CartItem item = findItemByProductId(productId);
        if (item == null) {
            throw new IllegalArgumentException("장바구니에 해당 상품이 없습니다.");
        }
        item.updateQuantity(quantity);
    }

    // 비즈니스 로직: 전체 금액 계산
    public BigDecimal getTotalAmount() {
        return items.stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // 비즈니스 로직: 장바구니 비우기
    public void clear() {
        this.items.clear();
    }

    // 헬퍼 메서드: 상품으로 아이템 찾기
    private CartItem findItemByProduct(Product product) {
        return items.stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .findFirst()
                .orElse(null);
    }

    // 헬퍼 메서드: 상품 ID로 아이템 찾기
    private CartItem findItemByProductId(Long productId) {
        return items.stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst()
                .orElse(null);
    }
}
