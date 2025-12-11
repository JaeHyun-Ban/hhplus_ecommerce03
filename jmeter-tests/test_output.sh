#!/bin/bash
JTL_FILE="results/coupon-test-20251206_182642.jtl"
TOTAL=$(tail -n +2 "$JTL_FILE" | wc -l | tr -d ' ')
SUCCESS=$(tail -n +2 "$JTL_FILE" | cut -d',' -f4 | grep -c "^200$" || echo "0")
SOLD_OUT=$(tail -n +2 "$JTL_FILE" | cut -d',' -f4 | grep -c "^410$" || echo "0")
FAILED=$((TOTAL - SUCCESS - SOLD_OUT))

echo "=== 쿠폰 테스트 결과 요약 ==="
echo "총 요청: ${TOTAL}"
echo "✅ 성공 (쿠폰 발급): ${SUCCESS}개"
echo "⏹  쿠폰 소진 (정상): ${SOLD_OUT}개"

if [ "$FAILED" -gt 0 ]; then
    echo "❌ 에러 발생: ${FAILED}개"
fi

echo ""
if [ "$SUCCESS" -eq 100 ]; then
    echo "🎉 동시성 제어 성공: 정확히 100개만 발급됨!"
else
    echo "❌ 동시성 제어 실패: ${SUCCESS}개 발급됨 (예상: 100개)"
fi
