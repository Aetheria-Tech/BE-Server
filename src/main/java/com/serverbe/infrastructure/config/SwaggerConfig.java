package com.serverbe.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger(OpenAPI) 상세 설정 클래스입니다.
 * JWT 보안 스키마를 정의하여 Swagger UI에서 인증 테스트를 가능하게 합니다.
 */
@Configuration
public class SwaggerConfig {

    // http://localhost:8080/swagger-ui/index.html로 접속
    @Bean
    public OpenAPI openAPI() {
        String jwtSchemeName = "jwtAuth"; // 보안 스키마 식별자

        // API 요청 시 'jwtAuth' 스키마를 사용하도록 요구사항 설정
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);

        // SecurityScheme 정의 (Bearer JWT 방식)
        Components components = new Components()
                .addSecuritySchemes(jwtSchemeName, new SecurityScheme()
                        .name(jwtSchemeName)
                        .type(SecurityScheme.Type.HTTP) // HTTP 방식
                        .scheme("bearer")             // bearer 접두어 사용
                        .bearerFormat("JWT"));         // 포맷은 JWT

        return new OpenAPI()
                .info(apiInfo())
                .addSecurityItem(securityRequirement) // 전역 보안 설정 적용
                .components(components);
    }

    private Info apiInfo() {
        return new Info()
                .title("Aetheria API Document")
                .description("Aetheria 백엔드 REST API 명세서")
                .version("1.0.0");
    }
}