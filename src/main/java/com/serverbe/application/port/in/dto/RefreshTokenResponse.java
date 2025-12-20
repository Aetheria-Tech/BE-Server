package com.serverbe.application.port.in.dto;

import java.time.Instant;

/**
 * 토큰 재발급 또는 발급 유스케이스의 실행 결과를 담는 객체입니다.
 * @param opaqueToken  새로 발급된 액세스 토큰
 * @param name 새로 발급된 리프레시 토큰
 * @param expiredAt    리프레시 토큰의 만료 시점
 */
public record RefreshTokenResponse(
    String opaqueToken,
    String name,
    Instant expiredAt
) {
    public static RefreshTokenResponse of(String opaqueToken, String name, Instant expiredAt) {
        return new RefreshTokenResponse(opaqueToken, name, expiredAt);
    }
}