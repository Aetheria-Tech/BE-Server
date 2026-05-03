package com.serverbe.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param auth      카카오 인증 및 API 통신 타임아웃 관련 상세 설정 {@link Auth}
 * @param adminKey  카카오 애플리케이션의 관리자 키 (서버 측 관리 기능에 사용)
 * @param clientId  카카오 애플리케이션의 REST API 키
 * @param geocoding 카카오 주소/좌표 변환(Geocoding) 서비스 관련 설정 {@link Geocoding}
 * @responsibility <b>카카오(Kakao)</b>에서 제공하는 소셜 로그인 및 위치 기반 서비스 연동을 위한 설정 정보를 관리합니다.
 * @implSpec 설정 파일(application.yml)에서 <b>kakao</b> 접두사로 시작하는 값을 계층 구조로 바인딩합니다.
 */
@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(
        Auth auth,
        String adminKey,
        String clientId,
        Geocoding geocoding
) {
    /**
     * @param redirectUri            인가 코드(Authorization Code)를 전달받을 서비스 내 리다이렉트 경로
     * @param kauth                  카카오 인증 서버(KAuth) 기본 URL
     * @param kapi                   카카오 리소스 서버(KApi) 기본 URL
     * @param connectTimeoutMillis   서버 연결 시도 제한 시간
     * @param responseTimeoutSeconds 서버 응답 대기 제한 시간
     * @param timeOutSeconds         전체 요청 처리 제한 시간
     * @responsibility 카카오 OAuth 2.0 인증 절차와 리소스 API 호출 시의 엔드포인트 및 통신 정책을 정의합니다.
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
     * @param dapi       카카오 로컬/지도 API 통합 기본 URL
     * @param geocodeApi 특정 주소를 좌표로 변환하거나 그 반대를 수행하는 상세 API 경로
     * @responsibility <b>런닝 아트</b> 서비스에 필요한 좌표 데이터 처리 및 주소 변환 API 설정을 관리합니다.
     */
    public record Geocoding(
            String dapi,
            String geocodeApi
    ) {
    }
}