# Swagger/OpenAPI 사용 가이드

## 목차
1. [OpenAPI 문서 위치](#1-openapi-문서-위치)
2. [Swagger UI 통합 방법](#2-swagger-ui-통합-방법)
3. [온라인 에디터로 확인](#3-온라인-에디터로-확인)
4. [Springdoc OpenAPI 통합](#4-springdoc-openapi-통합)
5. [API 테스트 방법](#5-api-테스트-방법)

---

## 1. OpenAPI 문서 위치

**파일 경로:** `docs/api/openapi.yaml`

이 파일은 OpenAPI 3.0 스펙으로 작성된 API 명세서입니다.

### 문서 구조

```yaml
openapi: 3.0.3
info:           # API 정보
  title: E-Commerce API
  version: 1.0.0

servers:        # 서버 정보
  - url: http://localhost:8080

tags:           # API 그룹
  - Users
  - Products
  - Cart
  - Orders
  - Coupons

paths:          # 엔드포인트 정의
  /users:
    post: ...

components:     # 재사용 가능한 스키마
  schemas:
  responses:
  parameters:
```

---

## 2. Swagger UI 통합 방법

### 옵션 1: Springdoc OpenAPI (권장)

#### 2.1 의존성 추가

`build.gradle`에 추가:
```gradle
dependencies {
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.2.0'
}
```

#### 2.2 설정

`application.yml`에 추가:
```yaml
springdoc:
  api-docs:
    path: /api-docs
    enabled: true
  swagger-ui:
    path: /swagger-ui.html
    enabled: true
    tags-sorter: alpha
    operations-sorter: alpha
  packages-to-scan: com.hhplus.ecommerce
  paths-to-match: /**
```

#### 2.3 실행

```bash
./gradlew bootRun

# Swagger UI 접속
open http://localhost:8080/swagger-ui.html

# OpenAPI JSON
open http://localhost:8080/api-docs
```

### 옵션 2: 기존 OpenAPI YAML 파일 사용

#### 2.1 설정 클래스 추가

`src/main/java/com/hhplus/ecommerce/config/OpenApiConfig.java`:
```java
package com.hhplus.ecommerce.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("E-Commerce API")
                        .version("1.0.0")
                        .description("이커머스 플랫폼 API")
                        .contact(new Contact()
                                .name("API Support")
                                .email("support@ecommerce.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("로컬 개발 서버"),
                        new Server()
                                .url("https://dev-api.ecommerce.com")
                                .description("개발 서버")
                ));
    }
}
```

#### 2.2 컨트롤러에 어노테이션 추가

```java
package com.hhplus.ecommerce.api.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "사용자 관리 API")
public class UserController {

    @Operation(
        summary = "사용자 조회",
        description = "사용자 ID로 사용자 정보를 조회합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UserResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "사용자를 찾을 수 없음"
        )
    })
    @GetMapping("/{userId}")
    public UserResponse getUser(
        @Parameter(description = "사용자 ID", required = true)
        @PathVariable Long userId
    ) {
        // ...
    }
}
```

---

## 3. 온라인 에디터로 확인

### 3.1 Swagger Editor

1. https://editor.swagger.io 접속
2. 왼쪽 에디터에 `docs/api/openapi.yaml` 내용 붙여넣기
3. 오른쪽에서 실시간 미리보기 확인

### 3.2 Swagger UI 직접 호스팅

#### Docker로 Swagger UI 실행

```bash
# OpenAPI 파일이 있는 디렉토리에서
docker run -p 8081:8080 \
  -e SWAGGER_JSON=/openapi.yaml \
  -v $(pwd)/docs/api/openapi.yaml:/openapi.yaml \
  swaggerapi/swagger-ui

# 접속
open http://localhost:8081
```

### 3.3 Postman으로 Import

1. Postman 실행
2. Import → Upload Files
3. `docs/api/openapi.yaml` 선택
4. Collection 자동 생성

---

## 4. Springdoc OpenAPI 통합 (상세)

### 4.1 build.gradle 전체 설정

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    // Springdoc OpenAPI
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.2.0'

    // ... 나머지 의존성
}
```

### 4.2 application.yml 전체 설정

```yaml
spring:
  application:
    name: ecommerce

springdoc:
  api-docs:
    path: /api-docs
    enabled: true
  swagger-ui:
    path: /swagger-ui.html
    enabled: true
    doc-expansion: none        # 기본 접힘
    tags-sorter: alpha         # 태그 알파벳 정렬
    operations-sorter: alpha   # 작업 알파벳 정렬
    display-request-duration: true
    default-models-expand-depth: 1
    default-model-expand-depth: 1
    try-it-out-enabled: true   # Try it out 버튼 활성화
  packages-to-scan: com.hhplus.ecommerce
  paths-to-match: /**
  default-consumes-media-type: application/json
  default-produces-media-type: application/json
```

### 4.3 보안 설정 (JWT)

```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT 토큰을 입력하세요")))
                .security(List.of(new SecurityRequirement().addList("bearerAuth")))
                .info(new Info()
                        .title("E-Commerce API")
                        .version("1.0.0"));
    }
}
```

---

## 5. API 테스트 방법

### 5.1 Swagger UI에서 테스트

1. **Swagger UI 접속**
   ```
   http://localhost:8080/swagger-ui.html
   ```

2. **인증 (JWT 필요 시)**
   - 우측 상단 "Authorize" 버튼 클릭
   - Bearer Token 입력
   - Authorize 클릭

3. **API 테스트**
   - 테스트할 API 선택
   - "Try it out" 클릭
   - 파라미터 입력
   - "Execute" 클릭
   - 응답 확인

### 5.2 cURL로 테스트

Swagger UI에서 cURL 명령어를 복사할 수 있습니다:

```bash
# 사용자 조회
curl -X 'GET' \
  'http://localhost:8080/users/1' \
  -H 'accept: application/json'

# 잔액 충전
curl -X 'POST' \
  'http://localhost:8080/users/1/balance/charge' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
  "amount": 10000
}'

# 주문 생성
curl -X 'POST' \
  'http://localhost:8080/orders' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
  "userId": 1,
  "items": [
    {
      "productId": 1,
      "quantity": 2
    }
  ],
  "userCouponIds": [1],
  "idempotencyKey": "order-20251028-123456"
}'
```

### 5.3 Postman으로 테스트

1. Postman에서 Import
2. `docs/api/openapi.yaml` 가져오기
3. 환경 변수 설정
   ```json
   {
     "baseUrl": "http://localhost:8080",
     "token": "your-jwt-token"
   }
   ```
4. Collection Runner로 일괄 테스트

### 5.4 HTTPie로 테스트

```bash
# HTTPie 설치
brew install httpie

# 사용자 조회
http GET http://localhost:8080/users/1

# 잔액 충전
http POST http://localhost:8080/users/1/balance/charge \
  amount:=10000

# 주문 생성
http POST http://localhost:8080/orders \
  userId:=1 \
  items:='[{"productId":1,"quantity":2}]' \
  userCouponIds:='[1]' \
  idempotencyKey="order-20251028-123456"
```

---

## 6. OpenAPI 문서 검증

### 6.1 온라인 검증

https://apitools.dev/swagger-parser/online/ 에서 YAML 파일 검증

### 6.2 CLI 검증

```bash
# swagger-cli 설치
npm install -g @apidevtools/swagger-cli

# 검증
swagger-cli validate docs/api/openapi.yaml

# 번들링 (하나의 파일로)
swagger-cli bundle docs/api/openapi.yaml -o docs/api/openapi-bundle.yaml
```

---

## 7. 주요 URL 정리

| 용도 | URL |
|-----|-----|
| **Swagger UI** | http://localhost:8080/swagger-ui.html |
| **OpenAPI JSON** | http://localhost:8080/api-docs |
| **OpenAPI YAML** | http://localhost:8080/api-docs.yaml |
| **H2 Console** | http://localhost:8080/h2-console |

---

## 8. 문제 해결

### 8.1 Swagger UI가 안 보여요

**원인:** Springdoc 의존성이 없거나 설정이 잘못됨

**해결:**
```gradle
// build.gradle 확인
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.2.0'
```

```bash
# 재빌드
./gradlew clean build
./gradlew bootRun
```

### 8.2 API가 Swagger UI에 안 나타나요

**원인:** 컨트롤러가 스캔 범위에 없음

**해결:**
```yaml
# application.yml
springdoc:
  packages-to-scan: com.hhplus.ecommerce
  paths-to-match: /**
```

### 8.3 CORS 에러가 발생해요

**원인:** 프론트엔드에서 API 호출 시 CORS 설정 필요

**해결:**
```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
```

---

## 9. 프로덕션 배포 시 주의사항

### 9.1 Swagger UI 비활성화

운영 환경에서는 Swagger UI를 비활성화하는 것이 좋습니다:

```yaml
# application-prod.yml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

### 9.2 프로파일별 설정

```yaml
# application-dev.yml
springdoc:
  swagger-ui:
    enabled: true

# application-prod.yml
springdoc:
  swagger-ui:
    enabled: false
```

---

## 10. 참고 자료

- [OpenAPI 3.0 Specification](https://swagger.io/specification/)
- [Springdoc OpenAPI Documentation](https://springdoc.org/)
- [Swagger UI](https://swagger.io/tools/swagger-ui/)
- [Swagger Editor](https://editor.swagger.io/)
