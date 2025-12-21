package com.serverbe.application.port.in.dto.oauth;

public record SocialTokenRefreshResponse(
    String accessToken,
    String refreshToken, // 구글은 보통 안 주지만, 카카오는 만료 임박 시 새로 줍니다.
    Integer expiresIn    // 액세스 토큰 만료 시간
) {}