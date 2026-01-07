package com.serverbe.adapter.in.web.dto.auth;

import com.serverbe.application.port.out.dto.oauth.AccessTokenResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "액세스 토큰 응답 정보")
public record AccessTokenResponse(
        @Schema(description = "서비스 전용 액세스 토큰 (JWT)", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String accessToken,

        @Schema(description = "토큰 만료 시간 (초 단위)", example = "3600")
        long expireIn
) {
    /**
     * Application 계층의 Result 객체를 Web 계층의 Response 객체로 변환합니다.
     */
    public static AccessTokenResponse toResponse(AccessTokenResult result) {
        return new AccessTokenResponse(result.accessToken(), result.expireIn());
    }
}