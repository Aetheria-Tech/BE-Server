package com.serverbe.application.port.in.dto;

import com.serverbe.domain.model.vo.Role;

/**
 * 로그인 성공 후 우리 서비스 전용 토큰 정보를 담아 클라이언트에게 반환하는 DTO입니다.
 */
public record TokenResponse(
        AccessTokenResponse accessTokenResponse,
        RefreshTokenResponse refreshTokenResponse,
        Role role // 프론트엔드에서 메뉴 노출 권한 등을 제어할 때 유용합니다.
) {
    // 정적 팩토리 메서드 (필요 시)
    public static TokenResponse of(AccessTokenResponse accessTokenResponse, RefreshTokenResponse refreshTokenResponse, Role role) {
        return new TokenResponse(accessTokenResponse, refreshTokenResponse, role);
    }
}