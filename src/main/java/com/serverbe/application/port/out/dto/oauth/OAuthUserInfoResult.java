package com.serverbe.application.port.out.dto.oauth;

import com.serverbe.domain.model.user.vo.OAuthProvider;

/**
 * @responsibility 외부 OAuth 제공자로부터 획득한 사용자 정보를 시스템 표준 형식으로 통합하여 전달하는 객체입니다.
 * @param oauthId 소셜 서비스에서 발급한 사용자의 고유 식별자
 * @param provider 해당 정보를 제공한 소셜 서비스 종류 {@link OAuthProvider}
 * @param email 사용자의 이메일 주소
 * @param nickname 사용자의 소셜 서비스 닉네임
 * @param oauthRefreshToken 소셜 서비스에서 발급한 리프레시 토큰 (보안 저장이 필요한 정보)
 */
public record OAuthUserInfoResult(
        String oauthId,
        OAuthProvider provider,
        String email,
        String nickname,
        String oauthRefreshToken
) {
}