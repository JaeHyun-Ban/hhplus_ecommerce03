#!/bin/bash

# 프로젝트 구조 리팩토링 스크립트
# Layer-First → Feature-First 구조로 변경

set -e  # 오류 발생 시 중단

echo "========================================="
echo "프로젝트 구조 리팩토링 시작"
echo "Layer-First → Feature-First"
echo "========================================="
echo ""

BASE_DIR="src/main/java/com/hhplus/ecommerce"
TEST_DIR="src/test/java/com/hhplus/ecommerce"

# 1단계: 새로운 기능별 디렉토리 구조 생성
echo "📁 1단계: 새로운 디렉토리 구조 생성..."

FEATURES=("cart" "coupon" "order" "product" "user")

for feature in "${FEATURES[@]}"; do
    echo "  Creating $feature structure..."
    mkdir -p "$BASE_DIR/$feature/application"
    mkdir -p "$BASE_DIR/$feature/domain"
    mkdir -p "$BASE_DIR/$feature/infrastructure/persistence"
    mkdir -p "$BASE_DIR/$feature/presentation/api"
    mkdir -p "$BASE_DIR/$feature/presentation/dto"
done

# 공통 모듈 디렉토리
echo "  Creating common modules..."
mkdir -p "$BASE_DIR/common"
mkdir -p "$BASE_DIR/config"
mkdir -p "$BASE_DIR/exception"
mkdir -p "$BASE_DIR/integration/domain"
mkdir -p "$BASE_DIR/integration/infrastructure/persistence"

echo "✅ 디렉토리 구조 생성 완료"
echo ""

# 2단계: 파일 이동 (git mv 사용)
echo "🚀 2단계: 파일 이동 시작..."

# Cart 이동
echo "  Moving cart files..."
if [ -d "$BASE_DIR/application/cart" ]; then
    git mv "$BASE_DIR/application/cart"/* "$BASE_DIR/cart/application/" 2>/dev/null || true
fi
if [ -d "$BASE_DIR/domain/cart" ]; then
    git mv "$BASE_DIR/domain/cart"/* "$BASE_DIR/cart/domain/" 2>/dev/null || true
fi
if [ -d "$BASE_DIR/infrastructure/persistence/cart" ]; then
    git mv "$BASE_DIR/infrastructure/persistence/cart"/* "$BASE_DIR/cart/infrastructure/persistence/" 2>/dev/null || true
fi
if [ -d "$BASE_DIR/presentation/api/cart" ]; then
    git mv "$BASE_DIR/presentation/api/cart"/* "$BASE_DIR/cart/presentation/api/" 2>/dev/null || true
fi

# Coupon 이동
echo "  Moving coupon files..."
if [ -d "$BASE_DIR/application/coupon" ]; then
    git mv "$BASE_DIR/application/coupon"/* "$BASE_DIR/coupon/application/" 2>/dev/null || true
fi
if [ -d "$BASE_DIR/domain/coupon" ]; then
    git mv "$BASE_DIR/domain/coupon"/* "$BASE_DIR/coupon/domain/" 2>/dev/null || true
fi
if [ -d "$BASE_DIR/infrastructure/persistence/coupon" ]; then
    git mv "$BASE_DIR/infrastructure/persistence/coupon"/* "$BASE_DIR/coupon/infrastructure/persistence/" 2>/dev/null || true
fi
if [ -d "$BASE_DIR/presentation/api/coupon" ]; then
    # dto 디렉토리가 있으면 먼저 이동
    if [ -d "$BASE_DIR/presentation/api/coupon/dto" ]; then
        git mv "$BASE_DIR/presentation/api/coupon/dto"/* "$BASE_DIR/coupon/presentation/dto/" 2>/dev/null || true
    fi
    git mv "$BASE_DIR/presentation/api/coupon"/*.java "$BASE_DIR/coupon/presentation/api/" 2>/dev/null || true
fi

# Order 이동
echo "  Moving order files..."
if [ -d "$BASE_DIR/application/order" ]; then
    git mv "$BASE_DIR/application/order"/* "$BASE_DIR/order/application/" 2>/dev/null || true
fi
if [ -d "$BASE_DIR/domain/order" ]; then
    git mv "$BASE_DIR/domain/order"/* "$BASE_DIR/order/domain/" 2>/dev/null || true
fi
if [ -d "$BASE_DIR/infrastructure/persistence/order" ]; then
    git mv "$BASE_DIR/infrastructure/persistence/order"/* "$BASE_DIR/order/infrastructure/persistence/" 2>/dev/null || true
fi
if [ -d "$BASE_DIR/presentation/api/order" ]; then
    if [ -d "$BASE_DIR/presentation/api/order/dto" ]; then
        git mv "$BASE_DIR/presentation/api/order/dto"/* "$BASE_DIR/order/presentation/dto/" 2>/dev/null || true
    fi
    git mv "$BASE_DIR/presentation/api/order"/*.java "$BASE_DIR/order/presentation/api/" 2>/dev/null || true
fi

# Product 이동
echo "  Moving product files..."
if [ -d "$BASE_DIR/application/product" ]; then
    git mv "$BASE_DIR/application/product"/* "$BASE_DIR/product/application/" 2>/dev/null || true
fi
if [ -d "$BASE_DIR/domain/product" ]; then
    git mv "$BASE_DIR/domain/product"/* "$BASE_DIR/product/domain/" 2>/dev/null || true
fi
if [ -d "$BASE_DIR/infrastructure/persistence/product" ]; then
    git mv "$BASE_DIR/infrastructure/persistence/product"/* "$BASE_DIR/product/infrastructure/persistence/" 2>/dev/null || true
fi
if [ -d "$BASE_DIR/presentation/api/product" ]; then
    git mv "$BASE_DIR/presentation/api/product"/*.java "$BASE_DIR/product/presentation/api/" 2>/dev/null || true
fi

# User 이동
echo "  Moving user files..."
if [ -d "$BASE_DIR/application/user" ]; then
    git mv "$BASE_DIR/application/user"/* "$BASE_DIR/user/application/" 2>/dev/null || true
fi
if [ -d "$BASE_DIR/domain/user" ]; then
    git mv "$BASE_DIR/domain/user"/* "$BASE_DIR/user/domain/" 2>/dev/null || true
fi
if [ -d "$BASE_DIR/infrastructure/persistence/user" ]; then
    git mv "$BASE_DIR/infrastructure/persistence/user"/* "$BASE_DIR/user/infrastructure/persistence/" 2>/dev/null || true
fi
if [ -d "$BASE_DIR/presentation/api/user" ]; then
    if [ -d "$BASE_DIR/presentation/api/user/dto" ]; then
        git mv "$BASE_DIR/presentation/api/user/dto"/* "$BASE_DIR/user/presentation/dto/" 2>/dev/null || true
    fi
    git mv "$BASE_DIR/presentation/api/user"/*.java "$BASE_DIR/user/presentation/api/" 2>/dev/null || true
fi

# 공통 모듈 이동
echo "  Moving common modules..."
if [ -d "$BASE_DIR/domain/common" ]; then
    git mv "$BASE_DIR/domain/common"/* "$BASE_DIR/common/" 2>/dev/null || true
fi
if [ -d "$BASE_DIR/config" ] && [ "$(ls -A $BASE_DIR/config)" ]; then
    # config는 이미 올바른 위치에 있음 (유지)
    echo "    Config already in correct location"
fi
if [ -d "$BASE_DIR/presentation/exception" ]; then
    git mv "$BASE_DIR/presentation/exception"/* "$BASE_DIR/exception/" 2>/dev/null || true
fi
if [ -d "$BASE_DIR/domain/integration" ]; then
    git mv "$BASE_DIR/domain/integration"/* "$BASE_DIR/integration/domain/" 2>/dev/null || true
fi
if [ -d "$BASE_DIR/infrastructure/persistence/integration" ]; then
    git mv "$BASE_DIR/infrastructure/persistence/integration"/* "$BASE_DIR/integration/infrastructure/persistence/" 2>/dev/null || true
fi

echo "✅ 파일 이동 완료"
echo ""

# 3단계: 빈 디렉토리 삭제
echo "🧹 3단계: 빈 디렉토리 정리..."
find "$BASE_DIR" -type d -empty -delete 2>/dev/null || true
echo "✅ 정리 완료"
echo ""

echo "========================================="
echo "✅ 구조 변경 1단계 완료!"
echo "========================================="
echo ""
echo "다음 단계:"
echo "1. import 문 수정 스크립트 실행"
echo "2. 테스트 파일 재구성"
echo "3. 전체 빌드 및 테스트"
