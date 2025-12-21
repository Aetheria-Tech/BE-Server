package com.serverbe.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 카카오 API 관련 설정 정보 객체 (Record)
 *
 * @param auth      카카오 인증 관련 설정
 * @param clientId  REST API 키
 * @param geocoding 카카오 주소/좌표 변환 관련 설정 (확장 대비)
 */
@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(
        Auth auth,
        String adminKey,
        String clientId,
        Geocoding geocoding
) {
    /**
     * 카카오 인증 관련 상세 설정
     *
     * @param redirectUri            인가 코드를 받을 리다이렉트 URI
     * @param kauth                  카카오 인증 서버 기본 URL
     * @param kapi                   카카오 리소스 서버 기본 URL
     * @param connectTimeoutMillis   연결 타임아웃 (Duration으로 자동 변환)
     * @param responseTimeoutSeconds 응답 타임아웃 (Duration으로 자동 변환)
     * @param timeOutSeconds         전체 타임아웃 (Duration으로 자동 변환)
     */
    public record Auth(
            String redirectUri,
            String kauth,
            String kapi,
            Duration connectTimeoutMillis,
            Duration responseTimeoutSeconds,
            Duration timeOutSeconds
    ) {
    }

    /**
     * 지오코딩 관련 설정
     *
     * @param dapi 카카오 서버 URL
     * @param geocodeApi 카카오 지오코딩 URL
     */
    public record Geocoding(
            String dapi,
            String geocodeApi
    ) {
    }
}