package com.serverbe.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param auth 구글 인증 관련 상세 설정 정보 {@link Auth}
 * @responsibility <b>구글 소셜 로그인(OAuth 2.0)</b> 연동에 필요한 인증 정보 및 API 엔드포인트를 관리하는 프로퍼티 객체입니다.
 * @implSpec 설정 파일(application.yml)에서 <b>google</b> 접두사로 시작하는 설정값들을 계층 구조로 바인딩합니다.
 */
@ConfigurationProperties(prefix = "google")
public record GoogleProperties(
        Auth auth
) {
    /**
     * @param clientId     구글 클라우드 콘솔에서 발급받은 클라이언트 아이디
     * @param clientSecret 구글 클라우드 콘솔에서 발급받은 클라이언트 보안 비밀번호
     * @param redirectUri  사용자 인증 후 권한 코드를 전달받을 리다이렉트 경로
     * @param oauthApi     액세스 토큰 및 리프레시 토큰 발급을 위한 구글 인증 API 주소
     * @param api          사용자 정보를 조회하기 위한 구글 리소스 API 주소
     * @responsibility 구글 OAuth 2.0 인증 프로세스에 사용되는 핵심 클라이언트 정보와 API 주소를 정의합니다.
     */
    public record Auth(
            String clientId,
            String clientSecret,
            String redirectUri,
            String oauthApi,
            String api
    ) {
    }
}