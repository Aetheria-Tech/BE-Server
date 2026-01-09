package com.serverbe.application.port.out.dto.oauth;

import com.serverbe.domain.model.user.vo.Role;

/**
 * @param accessTokenResult  액세스 토큰 관련 정보
 * @param refreshTokenResult 리프레시 토큰 관련 정보
 * @param role               사용자의 시스템 권한 {@link Role}
 * @responsibility 로그인 또는 토큰 재발급 성공 시 발행되는 토큰 세트와 사용자 권한 정보를 담는 객체입니다.
 */
public record TokenResult(
        AccessTokenResult accessTokenResult,
        RefreshTokenResult refreshTokenResult,
        Role role
) {
    /**
     * @param accessTokenResult  액세스 토큰 결과 객체
     * @param refreshTokenResult 리프레시 토큰 결과 객체
     * @param role               사용자 권한
     * @return 생성된 토큰 결과 객체
     * @responsibility 구성 요소들을 받아 {@link TokenResult} 인스턴스를 생성합니다.
     */
    public static TokenResult of(AccessTokenResult accessTokenResult, RefreshTokenResult refreshTokenResult, Role role) {
        return new TokenResult(accessTokenResult, refreshTokenResult, role);
    }
}