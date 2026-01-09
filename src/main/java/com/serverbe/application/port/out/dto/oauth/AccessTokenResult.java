package com.serverbe.application.port.out.dto.oauth;

/**
 * @responsibility 시스템 리소스 접근 권한을 증명하는 <b>액세스 토큰</b> 정보를 전달하는 객체입니다.
 * @param accessToken 서비스 요청 시 인증 헤더에 포함할 액세스 토큰 값
 * @param expireIn 액세스 토큰의 유효 기간 (초 단위)
 */
public record AccessTokenResult(
        String accessToken,
        long expireIn
) {
    /**
     * @responsibility 제공된 정보를 바탕으로 {@link AccessTokenResult} 인스턴스를 생성합니다.
     * @param accessToken 액세스 토큰 값
     * @param expireIn 만료 시간
     * @return 생성된 액세스 토큰 결과 객체
     */
    public static AccessTokenResult of(String accessToken, long expireIn) {
        return new AccessTokenResult(accessToken, expireIn);
    }
}