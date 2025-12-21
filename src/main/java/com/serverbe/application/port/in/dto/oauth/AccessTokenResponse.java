package com.serverbe.application.port.in.dto.oauth;

public record AccessTokenResponse(
        String accessToken,
        long expireIn
) {
    public static AccessTokenResponse of(String accessToken, long expireIn) {
        return new AccessTokenResponse(accessToken, expireIn);
    }
}
