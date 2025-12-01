#!/bin/bash

# import 문 수정 스크립트
# Layer-First → Feature-First 구조 변경에 따른 package 경로 업데이트

set -e

echo "========================================="
echo "Import 문 수정 시작"
echo "========================================="
echo ""

BASE_DIR="src"

# 모든 Java 파일 찾기
JAVA_FILES=$(find "$BASE_DIR" -name "*.java")

echo "📝 Java 파일 개수: $(echo "$JAVA_FILES" | wc -l)"
echo ""

# Feature별 import 경로 변경
FEATURES=("cart" "coupon" "order" "product" "user")

for feature in "${FEATURES[@]}"; do
    echo "🔄 $feature 모듈 import 수정..."

    # application layer
    find "$BASE_DIR" -name "*.java" -type f -exec sed -i '' \
        "s|com\.hhplus\.ecommerce\.application\.$feature|com.hhplus.ecommerce.$feature.application|g" {} +

    # domain layer
    find "$BASE_DIR" -name "*.java" -type f -exec sed -i '' \
        "s|com\.hhplus\.ecommerce\.domain\.$feature|com.hhplus.ecommerce.$feature.domain|g" {} +

    # infrastructure layer
    find "$BASE_DIR" -name "*.java" -type f -exec sed -i '' \
        "s|com\.hhplus\.ecommerce\.infrastructure\.persistence\.$feature|com.hhplus.ecommerce.$feature.infrastructure.persistence|g" {} +

    # presentation api layer
    find "$BASE_DIR" -name "*.java" -type f -exec sed -i '' \
        "s|com\.hhplus\.ecommerce\.presentation\.api\.$feature|com.hhplus.ecommerce.$feature.presentation.api|g" {} +
done

echo ""
echo "🔄 공통 모듈 import 수정..."

# common module
find "$BASE_DIR" -name "*.java" -type f -exec sed -i '' \
    "s|com\.hhplus\.ecommerce\.domain\.common|com.hhplus.ecommerce.common|g" {} +

# exception module
find "$BASE_DIR" -name "*.java" -type f -exec sed -i '' \
    "s|com\.hhplus\.ecommerce\.presentation\.exception|com.hhplus.ecommerce.exception|g" {} +

# integration module
find "$BASE_DIR" -name "*.java" -type f -exec sed -i '' \
    "s|com\.hhplus\.ecommerce\.domain\.integration|com.hhplus.ecommerce.integration.domain|g" {} +

find "$BASE_DIR" -name "*.java" -type f -exec sed -i '' \
    "s|com\.hhplus\.ecommerce\.infrastructure\.persistence\.integration|com.hhplus.ecommerce.integration.infrastructure.persistence|g" {} +

echo "✅ Import 문 수정 완료"
echo ""

echo "========================================="
echo "✅ 구조 변경 2단계 완료!"
echo "========================================="
echo ""
echo "다음 단계:"
echo "1. 테스트 파일 재구성"
echo "2. 전체 빌드 및 테스트"
