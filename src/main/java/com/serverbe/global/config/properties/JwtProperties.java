package com.serverbe.global.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

/**
 * JWT 관련 설정 정보 객체 (Record)
 * @param secret HS512 알고리즘을 위한 64byte 이상의 비밀키
 * @param accessToken 액세스 토큰 설정
 * @param refreshToken 리프레시 토큰 설정
 * @param authorityKey 권한 식별 키 (auth)
 * @param allowedClockSkewSeconds 허용할 클락 스큐(초)
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    String secret,
    AccessToken accessToken,
    RefreshToken refreshToken,
    String authorityKey,
    long allowedClockSkewSeconds
) {
    /**
     * 액세스 토큰 관련 설정
     * @param header 인증 헤더명 (Authorization)
     * @param validityInMinute 유효 기간 (Spring이 15m 형식을 Duration으로 자동 변환)
     */
    public record AccessToken(
        String header,
        Duration validityInMinute
    ) { }

    /**
     * 리프레시 토큰 관련 설정
     * @param cookie 쿠키 키 이름
     * @param expirationDays 만료 기간 (일)
     * @param byteLength 리프레시 토큰 생성 시 사용할 바이트 길이
     */
    public record RefreshToken(
        String cookie,
        int expirationDays,
        int byteLength
    ) { }
}