package com.serverbe.application.port.in.token;

import com.serverbe.application.port.out.dto.oauth.TokenResult;

public interface ReissueUseCase {
    /**
     * @param accessToken  값은 유효하지만 기간이 지난 액세스 토큰(Redis에 블랙리스트로 등록되어있으면 안 됨)
     * @param refreshToken 값이 유효한 리프레시 토큰(Redis에 등록되어 있어야 함)
     * @param deviceId     사용자의 기기 식별자
     * @return 재발급된 토큰 번들
     * @requirement UC-TKN-01: 토큰 재발급
     */
    TokenResult reissue(String accessToken, String refreshToken, String deviceId);
}