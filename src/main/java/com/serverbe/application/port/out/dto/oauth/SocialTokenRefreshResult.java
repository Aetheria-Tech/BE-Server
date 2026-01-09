package com.serverbe.application.port.out.dto.oauth;

/**
 * @responsibility 외부 소셜 서비스(OAuth2)로부터 갱신된 토큰 정보를 전달받는 객체입니다.
 * @param accessToken 외부 소셜 서비스의 신규 액세스 토큰
 * @param refreshToken 외부 소셜 서비스의 신규 리프레시 토큰 (제공자에 따라 선택적으로 포함됨)
 * @param expiresIn 액세스 토큰의 만료 시간 (초 단위)
 */
public record SocialTokenRefreshResult(
        String accessToken,
        String refreshToken,
        Integer expiresIn
) {}