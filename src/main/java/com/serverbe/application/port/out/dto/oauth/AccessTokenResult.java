package com.serverbe.application.port.out.dto.oauth;

public record AccessTokenResult(
        String accessToken,
        long expireIn
) {
    public static AccessTokenResult of(String accessToken, long expireIn) {
        return new AccessTokenResult(accessToken, expireIn);
    }
}
