package com.hhplus.ecommerce.domain.product;

public enum StockTransactionType {
    INCREASE,     // 증가 (입고)
    DECREASE,     // 감소 (판매)
    ADJUSTMENT    // 조정 (재고 수정)
}
