package com.serverbe.application.port.out.dto.oauth;

import com.serverbe.domain.model.user.vo.Role;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 및 토큰 재발급 응답")
public record TokenResponse(
        @Schema(description = "액세스 토큰 상세 정보")
        AccessTokenResult accessTokenResult,

        @Schema(description = "리프레시 토큰 상세 정보 (보통 HttpOnly 쿠키로 설정되므로 바디에서는 무시될 수 있음)", hidden = true)
        RefreshTokenResult refreshTokenResult,

        @Schema(description = "사용자 권한", example = "USER")
        Role role
) {
    public static TokenResponse of(AccessTokenResult accessTokenResult, RefreshTokenResult refreshTokenResult, Role role) {
        return new TokenResponse(accessTokenResult, refreshTokenResult, role);
    }
}