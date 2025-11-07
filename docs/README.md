# E-Commerce 문서

---

## 📁 문서 구조

### 📋 API 명세서 (`api-specs/`)
API 엔드포인트 및 기술 명세

- **[API Overview](./api-specs/API_README.md)** - API 문서 메인 페이지
- **[RESTful API 엔드포인트](./api-specs/restful-api-endpoints.md)** - API 상세 가이드 (통합 완료)
- **[OpenAPI Spec](./assignment/openapi.yaml)** - Swagger/OpenAPI 3.0 명세서

### 🎨 설계 문서 (`design/`)
도메인 설계, 데이터 모델, 다이어그램

- **[도메인 설계](./design/domain-design.md)** - 엔티티 및 도메인 모델 (완전한 설계 문서)
- **[ERD 다이어그램](./design/erd-diagram.dbml)** - 데이터베이스 스키마 (dbdiagram.io)
- **[시퀀스 다이어그램 (Mermaid)](./design/sequence-diagrams-mermaid.md)** - Mermaid 형식
- **[용어사전](./design/GLOSSARY.md)** - Enum 및 주요 용어 정리

### 📖 가이드 (`guides/`)
환경 설정 및 사용 가이드

- **[Swagger 가이드](./guides/SWAGGER_GUIDE.md)** - Swagger UI 사용법

### 📝 요구사항 (`requirements/`)
비즈니스 요구사항 및 사용자 스토리

- **[요구사항 명세](./requirements/requirements.md)** - 비즈니스 요구사항
- **[사용자 스토리](./requirements/user-stories.md)** - 기능 명세 (21개 User Story)
- **[Use Cases](./requirements/use-cases.md)** - 상세 유스케이스 (시스템 동작 플로우)

### 📦 과제 산출물 (`assignment/`)
2주차 과제 제출용 파일

- **[ERD 다이어그램](./assignment/ERD.png)** - ERD 이미지
- **[시퀀스 다이어그램](./assignment/sequence.png)** - 시퀀스 다이어그램 이미지
- **[OpenAPI 명세](./assignment/openapi.yaml)** - API 명세서

---

## 🔍 빠른 링크

### API 테스트
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/api-docs
- **Mock Server**: http://localhost:3000

### 데이터베이스
- **ERD Viewer**: https://dbdiagram.io/d

### 주요 엔드포인트
```bash
# 사용자
GET  /users/{userId}
POST /users/{userId}/balance/charge

# 상품
GET  /products
GET  /products/popular

# 장바구니
GET  /carts/{userId}
POST /carts/{userId}/items

# 주문
POST /orders
GET  /orders/{orderId}

# 쿠폰
GET  /coupons
POST /coupons/{couponId}/issue
```

---

## 📂 폴더별 상세 설명

### `api-specs/` - API 명세서
REST API의 기술적 명세를 포함합니다. 프론트엔드와 백엔드 개발자가 API 계약을 이해하고 구현하는데 사용됩니다.

### `design/` - 설계 문서
시스템 아키텍처, 도메인 모델, 데이터베이스 스키마 등 시스템 설계와 관련된 모든 문서를 포함합니다.

### `guides/` - 가이드
개발 환경 설정, 도구 사용법 등 실무에 필요한 가이드 문서를 포함합니다.

### `requirements/` - 요구사항
비즈니스 요구사항, 사용자 스토리 등 프로젝트의 "무엇을" 정의하는 문서를 포함합니다.

### `assignment/` - 과제 산출물
제출용 파일들이 포함되어 있습니다. 수정하지 마세요.

---

**Last Updated**: 2025-11-04
**Version**: 2.0.0 (문서 구조 재정리)