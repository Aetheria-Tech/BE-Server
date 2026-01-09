package com.serverbe.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param secret                  HS512 알고리즘 서명에 사용되는 64바이트 이상의 비밀키
 * @param accessToken             액세스 토큰 상세 설정 {@link AccessToken}
 * @param refreshToken            리프레시 토큰 상세 설정 {@link RefreshToken}
 * @param authorityKey            토큰 내 권한 정보를 저장할 클레임(Claim) 키 이름
 * @param allowedClockSkewSeconds 서버 간 시간 차이를 허용할 클락 스큐(초)
 * @responsibility 시스템의 <b>JWT(JSON Web Token)</b> 생성 및 검증에 필요한 설정 정보를 관리하는 프로퍼티 객체입니다.
 * @implSpec 설정 파일(application.yml)에서 <b>jwt</b> 접두사 설정을 계층 구조로 바인딩합니다.
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
     * @param prefix           토큰 전달 시 사용할 접두사 (예: <b>Bearer</b>)
     * @param header           인증 토큰이 포함될 HTTP 헤더 이름 (예: <b>Authorization</b>)
     * @param validityInMinute 토큰의 유효 기간 (Spring에 의해 {@link Duration}으로 자동 변환됨)
     * @responsibility 단기 인증을 위한 <b>액세스 토큰</b>의 규격과 유효 기간을 정의합니다.
     */
    public record AccessToken(
            String prefix,
            String header,
            Duration validityInMinute
    ) {
    }

    /**
     * @param cookie         리프레시 토큰을 저장할 쿠키의 이름
     * @param expirationDays 리프레시 토큰의 유효 기간
     * @param byteLength     토큰 식별자 생성 시 사용할 무작위 바이트의 길이
     * @responsibility 토큰 재발급을 위한 <b>리프레시 토큰</b>의 저장소 및 만료 정책을 정의합니다.
     */
    public record RefreshToken(
            String cookie,
            Duration expirationDays,
            int byteLength
    ) {
    }
}