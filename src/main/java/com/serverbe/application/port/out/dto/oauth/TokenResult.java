package com.serverbe.application.port.out.dto.oauth;

import com.serverbe.domain.model.user.vo.Role;

public record TokenResult(
        AccessTokenResult accessTokenResult,
        RefreshTokenResult refreshTokenResult,
        Role role
) {
    public static TokenResult of(AccessTokenResult accessTokenResult, RefreshTokenResult refreshTokenResult, Role role) {
        return new TokenResult(accessTokenResult, refreshTokenResult, role);
    }
}