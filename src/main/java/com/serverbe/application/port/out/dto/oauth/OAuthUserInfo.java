package com.serverbe.application.port.out.dto.oauth;

import com.serverbe.domain.model.user.vo.OAuthProvider;

/**
 * 외부 OAuth 제공자(카카오, 구글)로부터 받은 사용자 정보를 통일된 형식으로 담는 DTO입니다.
 */
public record OAuthUserInfo(
        String oauthId,           // 소셜 서비스의 고유 식별자
        OAuthProvider provider,   // KAKAO, GOOGLE
        String email,             // 사용자 이메일
        String nickname,          // 사용자 닉네임
        String oauthRefreshToken  // 소셜 서비스에서 발급해준 리프레시 토큰 (DB 저장 및 암호화 대상)
) {
}