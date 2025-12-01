#!/bin/bash

# 테스트 파일 구조 리팩토링 스크립트
# Layer-First → Feature-First 구조로 변경

set -e

echo "========================================="
echo "테스트 파일 구조 리팩토링 시작"
echo "========================================="
echo ""

TEST_DIR="src/test/java/com/hhplus/ecommerce"

# 1단계: 새로운 기능별 디렉토리 구조 생성
echo "📁 1단계: 새로운 테스트 디렉토리 구조 생성..."

FEATURES=("cart" "coupon" "order" "product" "user")

for feature in "${FEATURES[@]}"; do
    echo "  Creating $feature test structure..."
    mkdir -p "$TEST_DIR/$feature/application"
    mkdir -p "$TEST_DIR/$feature/domain"
    mkdir -p "$TEST_DIR/$feature/infrastructure/persistence"
    mkdir -p "$TEST_DIR/$feature/presentation/api"
done

# 공통 모듈 디렉토리
echo "  Creating common test modules..."
mkdir -p "$TEST_DIR/config"
mkdir -p "$TEST_DIR/integration"

echo "✅ 테스트 디렉토리 구조 생성 완료"
echo ""

# 2단계: 테스트 파일 이동
echo "🚀 2단계: 테스트 파일 이동 시작..."

# Cart 테스트 이동
echo "  Moving cart test files..."
if [ -d "$TEST_DIR/application/cart" ]; then
    git mv "$TEST_DIR/application/cart"/* "$TEST_DIR/cart/application/" 2>/dev/null || true
fi
if [ -d "$TEST_DIR/domain/cart" ]; then
    git mv "$TEST_DIR/domain/cart"/* "$TEST_DIR/cart/domain/" 2>/dev/null || true
fi
if [ -d "$TEST_DIR/infrastructure/persistence/cart" ]; then
    git mv "$TEST_DIR/infrastructure/persistence/cart"/* "$TEST_DIR/cart/infrastructure/persistence/" 2>/dev/null || true
fi
if [ -d "$TEST_DIR/presentation/api/cart" ]; then
    git mv "$TEST_DIR/presentation/api/cart"/* "$TEST_DIR/cart/presentation/api/" 2>/dev/null || true
fi

# Coupon 테스트 이동
echo "  Moving coupon test files..."
if [ -d "$TEST_DIR/application/coupon" ]; then
    git mv "$TEST_DIR/application/coupon"/* "$TEST_DIR/coupon/application/" 2>/dev/null || true
fi
if [ -d "$TEST_DIR/domain/coupon" ]; then
    git mv "$TEST_DIR/domain/coupon"/* "$TEST_DIR/coupon/domain/" 2>/dev/null || true
fi
if [ -d "$TEST_DIR/infrastructure/persistence/coupon" ]; then
    git mv "$TEST_DIR/infrastructure/persistence/coupon"/* "$TEST_DIR/coupon/infrastructure/persistence/" 2>/dev/null || true
fi
if [ -d "$TEST_DIR/presentation/api/coupon" ]; then
    git mv "$TEST_DIR/presentation/api/coupon"/* "$TEST_DIR/coupon/presentation/api/" 2>/dev/null || true
fi

# Order 테스트 이동
echo "  Moving order test files..."
if [ -d "$TEST_DIR/application/order" ]; then
    git mv "$TEST_DIR/application/order"/* "$TEST_DIR/order/application/" 2>/dev/null || true
fi
if [ -d "$TEST_DIR/domain/order" ]; then
    git mv "$TEST_DIR/domain/order"/* "$TEST_DIR/order/domain/" 2>/dev/null || true
fi
if [ -d "$TEST_DIR/infrastructure/persistence/order" ]; then
    git mv "$TEST_DIR/infrastructure/persistence/order"/* "$TEST_DIR/order/infrastructure/persistence/" 2>/dev/null || true
fi
if [ -d "$TEST_DIR/presentation/api/order" ]; then
    git mv "$TEST_DIR/presentation/api/order"/* "$TEST_DIR/order/presentation/api/" 2>/dev/null || true
fi

# Product 테스트 이동
echo "  Moving product test files..."
if [ -d "$TEST_DIR/application/product" ]; then
    git mv "$TEST_DIR/application/product"/* "$TEST_DIR/product/application/" 2>/dev/null || true
fi
if [ -d "$TEST_DIR/domain/product" ]; then
    git mv "$TEST_DIR/domain/product"/* "$TEST_DIR/product/domain/" 2>/dev/null || true
fi
if [ -d "$TEST_DIR/infrastructure/persistence/product" ]; then
    git mv "$TEST_DIR/infrastructure/persistence/product"/* "$TEST_DIR/product/infrastructure/persistence/" 2>/dev/null || true
fi
if [ -d "$TEST_DIR/presentation/api/product" ]; then
    git mv "$TEST_DIR/presentation/api/product"/* "$TEST_DIR/product/presentation/api/" 2>/dev/null || true
fi

# User 테스트 이동
echo "  Moving user test files..."
if [ -d "$TEST_DIR/application/user" ]; then
    git mv "$TEST_DIR/application/user"/* "$TEST_DIR/user/application/" 2>/dev/null || true
fi
if [ -d "$TEST_DIR/domain/user" ]; then
    git mv "$TEST_DIR/domain/user"/* "$TEST_DIR/user/domain/" 2>/dev/null || true
fi
if [ -d "$TEST_DIR/infrastructure/persistence/user" ]; then
    git mv "$TEST_DIR/infrastructure/persistence/user"/* "$TEST_DIR/user/infrastructure/persistence/" 2>/dev/null || true
fi
if [ -d "$TEST_DIR/presentation/api/user" ]; then
    git mv "$TEST_DIR/presentation/api/user"/* "$TEST_DIR/user/presentation/api/" 2>/dev/null || true
fi

# 공통 테스트 파일 이동
echo "  Moving common test files..."
if [ -d "$TEST_DIR/config" ] && [ "$(ls -A $TEST_DIR/config 2>/dev/null)" ]; then
    echo "    Config test files already in correct location"
fi

echo "✅ 테스트 파일 이동 완료"
echo ""

# 3단계: 빈 디렉토리 삭제
echo "🧹 3단계: 빈 디렉토리 정리..."
find "$TEST_DIR" -type d -empty -delete 2>/dev/null || true
echo "✅ 정리 완료"
echo ""

echo "========================================="
echo "✅ 테스트 파일 구조 변경 완료!"
echo "========================================="
echo ""
echo "다음 단계:"
echo "1. 전체 빌드 및 테스트"
