#!/bin/bash

###############################################################################
# JMeter 성능 테스트 실행 스크립트
#
# 사용법:
#   ./run-tests.sh [test_name]
#
# 예시:
#   ./run-tests.sh coupon     # 쿠폰 발급 테스트만 실행
#   ./run-tests.sh ranking    # 랭킹 조회 테스트만 실행
#   ./run-tests.sh system     # 전체 시스템 성능 테스트만 실행
#   ./run-tests.sh all        # 모든 테스트 실행 (기본값)
###############################################################################

set -e  # 에러 발생 시 스크립트 중단

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 현재 디렉토리
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 타임스탬프 생성
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# 결과 디렉토리
RESULTS_DIR="results"
mkdir -p "$RESULTS_DIR"

###############################################################################
# 함수: JMeter 설치 확인
###############################################################################
check_jmeter() {
    echo -e "${BLUE}[1/6] JMeter 설치 확인...${NC}"

    if ! command -v jmeter &> /dev/null; then
        echo -e "${RED}❌ JMeter가 설치되어 있지 않습니다.${NC}"
        echo -e "${YELLOW}설치 방법:${NC}"
        echo -e "  brew install jmeter"
        exit 1
    fi

    JMETER_VERSION=$(jmeter -v 2>&1 | grep "Version" | head -1)
    echo -e "${GREEN}✅ JMeter 설치됨: $JMETER_VERSION${NC}"
}

###############################################################################
# 함수: 애플리케이션 헬스체크
###############################################################################
check_application() {
    echo -e "${BLUE}[2/6] 애플리케이션 헬스체크...${NC}"

    if ! curl -s http://localhost:8080/actuator/health > /dev/null; then
        echo -e "${RED}❌ 애플리케이션이 실행되고 있지 않습니다.${NC}"
        echo -e "${YELLOW}실행 방법:${NC}"
        echo -e "  ./gradlew bootRun"
        exit 1
    fi

    echo -e "${GREEN}✅ 애플리케이션 정상 실행 중${NC}"
}

###############################################################################
# 함수: Redis 확인
###############################################################################
check_redis() {
    echo -e "${BLUE}[3/6] Redis 연결 확인...${NC}"

    if ! redis-cli ping > /dev/null 2>&1; then
        echo -e "${RED}❌ Redis가 실행되고 있지 않습니다.${NC}"
        echo -e "${YELLOW}실행 방법:${NC}"
        echo -e "  redis-server"
        exit 1
    fi

    echo -e "${GREEN}✅ Redis 정상 실행 중${NC}"
}

###############################################################################
# 함수: 테스트 데이터 준비
###############################################################################
prepare_test_data() {
    echo -e "${BLUE}[4/6] 테스트 데이터 준비...${NC}"

    # MySQL 연결 확인
    if ! command -v mysql &> /dev/null; then
        echo -e "${YELLOW}⚠️  MySQL 클라이언트가 설치되어 있지 않습니다. 데이터 준비를 건너뜁니다.${NC}"
        echo -e "${YELLOW}   테스트 쿠폰(ID: 1)이 이미 존재하는지 확인하세요.${NC}"
        return 0
    fi

    # 테스트용 쿠폰이 이미 존재하는지 확인
    COUPON_EXISTS=$(mysql --protocol=TCP -h localhost -P 3306 -u root -p123123 -D mydb -N -B -e "SELECT COUNT(*) FROM coupons WHERE id = 1" 2>/dev/null || echo "0")

    if [ "$COUPON_EXISTS" -gt 0 ]; then
        echo -e "${GREEN}✅ 테스트 쿠폰(ID: 1)이 이미 존재합니다.${NC}"

        # Redis 초기화 (모든 쿠폰 관련 키 삭제)
        redis-cli DEL "coupon:issued:1" > /dev/null 2>&1
        redis-cli DEL "coupon:user:count:1" > /dev/null 2>&1
        redis-cli DEL "coupon:1:issued_count" > /dev/null 2>&1

        # DB의 발급 수량도 초기화
        mysql --protocol=TCP -h localhost -P 3306 -u root -p123123 -D mydb -e "UPDATE coupons SET issued_quantity = 0, status = 'ACTIVE' WHERE id = 1" 2>/dev/null

        echo -e "${GREEN}✅ 쿠폰 발급 카운터를 초기화했습니다.${NC}"
    else
        echo -e "${YELLOW}⚠️  테스트 쿠폰(ID: 1)이 존재하지 않습니다. 생성 중...${NC}"

        # 테스트용 쿠폰 생성
        mysql --protocol=TCP -h localhost -P 3306 -u root -p123123 -D mydb <<EOF 2>/dev/null
INSERT INTO coupons (
    id, code, name, description, type, discount_value,
    minimum_order_amount, maximum_discount_amount,
    total_quantity, issued_quantity, max_issue_per_user,
    issue_start_at, issue_end_at, valid_from, valid_until,
    status, created_at, updated_at
) VALUES (
    1,
    'TEST_COUPON_2025',
    'JMeter 성능 테스트용 쿠폰',
    '1000명이 100개 쿠폰에 동시 요청하는 테스트용 쿠폰입니다.',
    'FIXED_AMOUNT',
    5000.00,
    10000.00,
    NULL,
    100,
    0,
    1,
    '2025-01-01 00:00:00',
    '2025-12-31 23:59:59',
    '2025-01-01 00:00:00',
    '2025-12-31 23:59:59',
    'ACTIVE',
    NOW(),
    NOW()
)
ON DUPLICATE KEY UPDATE
    issued_quantity = 0,
    status = 'ACTIVE',
    updated_at = NOW();
EOF

        if [ $? -eq 0 ]; then
            echo -e "${GREEN}✅ 테스트 쿠폰을 생성했습니다.${NC}"
        else
            echo -e "${YELLOW}⚠️  쿠폰 생성에 실패했습니다. 수동으로 쿠폰을 준비해주세요.${NC}"
        fi
    fi

    # 테스트 사용자 생성 (최소 1000명)
    USER_COUNT=$(mysql --protocol=TCP -h localhost -P 3306 -u root -p123123 -D mydb -N -B -e "SELECT COUNT(*) FROM users" 2>/dev/null || echo "0")

    if [ "$USER_COUNT" -lt 1000 ]; then
        echo -e "${YELLOW}테스트 사용자 생성 중... (1000명, 약 10초 소요)${NC}"

        # Stored Procedure로 빠르게 사용자 생성
        mysql --protocol=TCP -h localhost -P 3306 -u root -p123123 -D mydb 2>/dev/null <<'EOF'
DROP PROCEDURE IF EXISTS create_test_users;
DELIMITER //
CREATE PROCEDURE create_test_users()
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= 1000 DO
        INSERT IGNORE INTO users (email, password, name, balance, role, status, created_at, updated_at)
        VALUES (
            CONCAT('testuser', i, '@test.com'),
            'password123',
            CONCAT('테스트사용자', i),
            100000.00,
            'USER',
            'ACTIVE',
            NOW(),
            NOW()
        );
        SET i = i + 1;
    END WHILE;
END //
DELIMITER ;
CALL create_test_users();
DROP PROCEDURE create_test_users;
EOF

        if [ $? -eq 0 ]; then
            FINAL_COUNT=$(mysql --protocol=TCP -h localhost -P 3306 -u root -p123123 -D mydb -N -B -e "SELECT COUNT(*) FROM users" 2>/dev/null)
            echo -e "${GREEN}✅ 테스트 사용자를 생성했습니다 (총 ${FINAL_COUNT}명).${NC}"
        else
            echo -e "${YELLOW}⚠️  사용자 생성에 실패했습니다. 기존 사용자로 테스트를 진행합니다.${NC}"
        fi
    else
        echo -e "${GREEN}✅ 충분한 테스트 사용자가 존재합니다 (${USER_COUNT}명).${NC}"
    fi
}

###############################################################################
# 함수: 쿠폰 발급 동시성 테스트
###############################################################################
run_coupon_test() {
    echo ""
    echo -e "${BLUE}[5/6] 선착순 쿠폰 발급 동시성 테스트 실행 중...${NC}"
    echo -e "${YELLOW}테스트 설정: 1,000명 동시 요청 → 100개 쿠폰 발급${NC}"

    TEST_NAME="coupon-test-$TIMESTAMP"
    JTL_FILE="$RESULTS_DIR/${TEST_NAME}.jtl"
    REPORT_DIR="$RESULTS_DIR/${TEST_NAME}-report"

    jmeter -n -t coupon-concurrency-test.jmx \
        -l "$JTL_FILE" \
        -e -o "$REPORT_DIR"

    echo -e "${GREEN}✅ 쿠폰 테스트 완료${NC}"
    echo -e "${BLUE}리포트 위치: $REPORT_DIR/index.html${NC}"

    # 결과 요약
    echo ""
    echo -e "${YELLOW}=== 쿠폰 테스트 결과 요약 ===${NC}"

    # JTL 파일에서 성공/실패 카운트 추출
    if [ -f "$JTL_FILE" ]; then
        # 헤더를 제외하고 카운트 (tail -n +2)
        TOTAL=$(tail -n +2 "$JTL_FILE" | wc -l | tr -d ' ')
        # 4번째 필드(응답 코드)가 정확히 200인 것만 카운트
        SUCCESS=$(tail -n +2 "$JTL_FILE" | cut -d',' -f4 | grep -c "^200$" || echo "0")
        # 410은 쿠폰 소진 (정상적인 비즈니스 로직)
        SOLD_OUT=$(tail -n +2 "$JTL_FILE" | cut -d',' -f4 | grep -c "^410$" || echo "0")
        FAILED=$((TOTAL - SUCCESS - SOLD_OUT))

        echo -e "총 요청: ${TOTAL}"
        echo -e "✅ 성공 (쿠폰 발급): ${GREEN}${SUCCESS}개${NC}"
        echo -e "⏹  쿠폰 소진 (정상): ${YELLOW}${SOLD_OUT}개${NC}"

        if [ "$FAILED" -gt 0 ]; then
            echo -e "❌ 에러 발생: ${RED}${FAILED}개${NC}"
        fi

        echo ""
        if [ "$SUCCESS" -eq 100 ]; then
            echo -e "${GREEN}🎉 동시성 제어 성공: 정확히 100개만 발급됨!${NC}"
        else
            echo -e "${RED}❌ 동시성 제어 실패: ${SUCCESS}개 발급됨 (예상: 100개)${NC}"
        fi
    fi

    # HTML 리포트 자동 열기
    if command -v open &> /dev/null; then
        echo ""
        echo -e "${BLUE}HTML 리포트를 여는 중...${NC}"
        open "$REPORT_DIR/index.html"
    fi
}

###############################################################################
# 함수: 인기상품 랭킹 조회 부하 테스트
###############################################################################
run_ranking_test() {
    echo ""
    echo -e "${BLUE}[5/6] 인기상품 랭킹 조회 부하 테스트 실행 중...${NC}"
    echo -e "${YELLOW}테스트 설정: 100 TPS, 60초 지속${NC}"

    TEST_NAME="ranking-test-$TIMESTAMP"
    JTL_FILE="$RESULTS_DIR/${TEST_NAME}.jtl"
    REPORT_DIR="$RESULTS_DIR/${TEST_NAME}-report"

    jmeter -n -t ranking-load-test.jmx \
        -l "$JTL_FILE" \
        -e -o "$REPORT_DIR"

    echo -e "${GREEN}✅ 랭킹 테스트 완료${NC}"
    echo -e "${BLUE}리포트 위치: $REPORT_DIR/index.html${NC}"

    # 결과 요약
    echo ""
    echo -e "${YELLOW}=== 랭킹 테스트 결과 요약 ===${NC}"

    if [ -f "$JTL_FILE" ]; then
        # 평균 응답 시간 계산 (간단한 추정)
        echo -e "상세 결과는 HTML 리포트를 확인하세요."
        echo -e "${BLUE}리포트: $REPORT_DIR/index.html${NC}"
    fi

    # HTML 리포트 자동 열기
    if command -v open &> /dev/null; then
        echo ""
        echo -e "${BLUE}HTML 리포트를 여는 중...${NC}"
        open "$REPORT_DIR/index.html"
    fi
}

###############################################################################
# 함수: 전체 시스템 성능 테스트
###############################################################################
run_full_system_test() {
    echo ""
    echo -e "${BLUE}전체 시스템 성능 테스트 실행 중...${NC}"
    echo -e "${YELLOW}테스트 설정: 50명 동시 사용자, 5분 지속, 6가지 시나리오${NC}"
    echo -e "${YELLOW}시나리오: 상품목록(60%) > 상품상세(50%) > 랭킹조회(40%) > 장바구니(30%) > 주문(20%) > 쿠폰(10%)${NC}"

    TEST_NAME="full-system-test-$TIMESTAMP"
    JTL_FILE="$RESULTS_DIR/${TEST_NAME}.jtl"
    REPORT_DIR="$RESULTS_DIR/${TEST_NAME}-report"

    jmeter -n -t full-system-performance-test.jmx \
        -l "$JTL_FILE" \
        -e -o "$REPORT_DIR"

    echo -e "${GREEN}✅ 전체 시스템 테스트 완료${NC}"
    echo -e "${BLUE}리포트 위치: $REPORT_DIR/index.html${NC}"

    # 결과 요약
    echo ""
    echo -e "${YELLOW}=== 전체 시스템 테스트 결과 요약 ===${NC}"

    if [ -f "$JTL_FILE" ]; then
        # 헤더를 제외하고 카운트 (tail -n +2)
        TOTAL=$(tail -n +2 "$JTL_FILE" | wc -l | tr -d ' ')
        SUCCESS=$(tail -n +2 "$JTL_FILE" | grep -c ",true," || echo "0")
        FAILED=$((TOTAL - SUCCESS))

        # 0으로 나누기 방지
        if [ "$TOTAL" -gt 0 ]; then
            ERROR_RATE=$(awk "BEGIN {printf \"%.2f\", ($FAILED / $TOTAL) * 100}")
        else
            ERROR_RATE="0.00"
        fi

        echo -e "총 요청: ${TOTAL}"
        echo -e "성공: ${GREEN}${SUCCESS}${NC}"
        echo -e "실패: ${RED}${FAILED}${NC}"
        echo -e "에러율: ${ERROR_RATE}%"

        if (( $(echo "$ERROR_RATE < 1.0" | bc -l) )); then
            echo -e "${GREEN}✅ 성능 기준 충족: 에러율 < 1%${NC}"
        else
            echo -e "${YELLOW}⚠️  주의: 에러율이 높습니다 (목표: < 1%)${NC}"
        fi

        echo -e "${BLUE}상세 메트릭은 HTML 리포트를 확인하세요.${NC}"
    fi

    # HTML 리포트 자동 열기
    if command -v open &> /dev/null; then
        echo ""
        echo -e "${BLUE}HTML 리포트를 여는 중...${NC}"
        open "$REPORT_DIR/index.html"
    fi
}

###############################################################################
# 함수: 정리 작업
###############################################################################
cleanup() {
    echo ""
    echo -e "${BLUE}[6/6] 정리 작업...${NC}"

    # 오래된 결과 파일 삭제 (30일 이상)
    find "$RESULTS_DIR" -name "*.jtl" -mtime +30 -delete 2>/dev/null || true
    find "$RESULTS_DIR" -type d -name "*-report" -mtime +30 -exec rm -rf {} + 2>/dev/null || true

    echo -e "${GREEN}✅ 정리 완료${NC}"
}

###############################################################################
# 메인 실행
###############################################################################
main() {
    echo -e "${GREEN}╔═══════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║     JMeter 성능 테스트 실행 스크립트              ║${NC}"
    echo -e "${GREEN}╚═══════════════════════════════════════════════════╝${NC}"
    echo ""

    TEST_TYPE="${1:-all}"

    # 사전 확인
    check_jmeter
    check_application
    check_redis
    prepare_test_data

    # 테스트 실행
    case "$TEST_TYPE" in
        coupon)
            run_coupon_test
            ;;
        ranking)
            run_ranking_test
            ;;
        system)
            run_full_system_test
            ;;
        all)
            run_coupon_test
            run_ranking_test
            run_full_system_test
            ;;
        *)
            echo -e "${RED}❌ 알 수 없는 테스트 타입: $TEST_TYPE${NC}"
            echo -e "${YELLOW}사용법: ./run-tests.sh [coupon|ranking|system|all]${NC}"
            exit 1
            ;;
    esac

    # 정리
    cleanup

    echo ""
    echo -e "${GREEN}╔═══════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║     모든 테스트 완료!                             ║${NC}"
    echo -e "${GREEN}╚═══════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${BLUE}결과 파일 위치: $RESULTS_DIR/${NC}"
    echo -e "${BLUE}리포트 확인: ls -lh $RESULTS_DIR/*-report/index.html${NC}"
}

# 스크립트 실행
main "$@"
