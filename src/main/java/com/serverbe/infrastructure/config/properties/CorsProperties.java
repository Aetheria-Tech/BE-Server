package com.serverbe.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * CORS 관련 설정을 관리하는 프로퍼티 클래스입니다.
 *
 * @param allowedOrigins 허용할 도메인 리스트
 */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(
        List<String> allowedOrigins
) {
}