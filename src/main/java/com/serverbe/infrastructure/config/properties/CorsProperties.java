package com.serverbe.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @param allowedOrigins 브라우저에서 리소스 접근을 허용할 신뢰할 수 있는 도메인(Origin) 리스트
 * @responsibility 애플리케이션의 <b>CORS(Cross-Origin Resource Sharing)</b> 정책 설정을 관리하는 프로퍼티 객체입니다.
 * @implSpec 설정 파일(application.yml)에서 <b>cors</b> 접두사로 시작하는 설정값들을 리스트 형태로 바인딩합니다.
 */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(
        List<String> allowedOrigins
) {
}