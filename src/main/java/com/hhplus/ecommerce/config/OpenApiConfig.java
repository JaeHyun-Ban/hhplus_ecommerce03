package com.hhplus.ecommerce.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
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
                        .description("""
                                ## 이커머스 플랫폼 API

                                이 API는 이커머스 플랫폼의 핵심 기능을 제공합니다.

                                ### 주요 기능
                                - 사용자 관리 및 잔액 충전
                                - 상품 조회 및 인기 상품 통계
                                - 장바구니 관리
                                - 주문 생성 및 결제
                                - 쿠폰 발급 및 사용

                                ### 인증
                                JWT Bearer 토큰을 사용합니다.
                                ```
                                Authorization: Bearer {token}
                                ```
                                """)
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
                                .description("개발 서버"),
                        new Server()
                                .url("https://api.ecommerce.com")
                                .description("운영 서버")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT 토큰을 입력하세요")))
                .security(List.of(new SecurityRequirement().addList("bearerAuth")));
    }
}
