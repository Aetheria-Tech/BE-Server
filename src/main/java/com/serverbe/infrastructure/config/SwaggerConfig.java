package com.serverbe.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @responsibility <b>OpenAPI 3.0(Swagger)</b>을 기반으로 시스템의 REST API 명세를 자동화하고 테스트 환경을 구축합니다.
 * @implSpec 인증이 필요한 API 테스트를 위해 <b>JWT(Bearer)</b> 보안 스키마를 전역적으로 설정합니다.
 */
@Configuration
public class SwaggerConfig {

    /**
     * @return 보안 설정 및 API 메타데이터가 포함된 {@link OpenAPI} 객체
     * @responsibility Swagger UI의 전역 설정 및 <b>JWT 인증</b>을 위한 보안 요구사항을 정의합니다.
     * @implNote 1. <b>SecurityScheme</b>: <b>bearer</b> 형식을 사용하는 JWT 인증 방식을 <b>jwtAuth</b>라는 이름으로 정의합니다.<br>
     * 2. <b>SecurityRequirement</b>: 정의된 보안 스키마를 모든 API 엔드포인트에 기본 요구사항으로 적용하여 UI 상에서 토큰 입력이 가능하게 합니다.
     */
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

    /**
     * @return API 정보 객체 {@link Info}
     * @responsibility API 문서 상단에 노출될 <b>제목, 설명, 버전</b> 등의 메타데이터를 정의합니다.
     */
    private Info apiInfo() {
        return new Info()
                .title("Aetheria API Document")
                .description("Aetheria 백엔드 REST API 명세서")
                .version("1.0.0");
    }
}