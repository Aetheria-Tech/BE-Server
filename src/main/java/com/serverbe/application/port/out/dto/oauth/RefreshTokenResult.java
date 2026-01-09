package com.serverbe.application.port.out.dto.oauth;

import java.time.Instant;

/**
 * @responsibility 토큰 발급 및 재발급 프로세스에서 생성된 리프레시 토큰 정보를 전달하는 객체입니다.
 * @param opaqueToken 새로 발급된 리프레시 토큰의 실제 값
 * @param name 토큰을 식별하거나 저장소에서 관리하기 위한 명칭
 * @param expiredAt 토큰의 만료 시점을 나타내는 {@link Instant}
 */
public record RefreshTokenResult(
        String opaqueToken,
        String name,
        Instant expiredAt
) {
    /**
     * @responsibility 제공된 정보를 바탕으로 {@link RefreshTokenResult} 인스턴스를 생성합니다.
     * @param opaqueToken 리프레시 토큰 값
     * @param name 토큰 식별 명칭
     * @param expiredAt 만료 시각
     * @return 생성된 리프레시 토큰 결과 객체
     */
    public static RefreshTokenResult of(String opaqueToken, String name, Instant expiredAt) {
        return new RefreshTokenResult(opaqueToken, name, expiredAt);
    }
}