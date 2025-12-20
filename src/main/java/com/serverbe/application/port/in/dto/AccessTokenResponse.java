package com.serverbe.application.port.in.dto;

import java.time.Instant;

public record AccessTokenResponse(
        String accessToken,
        long expireIn
) {
    public static AccessTokenResponse of(String accessToken, long expireIn) {
        return new AccessTokenResponse(accessToken, expireIn);
    }
}
