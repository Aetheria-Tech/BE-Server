package com.serverbe.application.port.out.oauth;


import com.serverbe.application.port.out.dto.oauth.OAuthUserInfoResult;
import com.serverbe.application.port.out.dto.oauth.SocialTokenRefreshResult;
import com.serverbe.domain.model.user.vo.OAuthProvider;
import reactor.core.publisher.Mono;

public interface OAuthClientPort {
    /**
     * 인가 코드로 해당 플랫폼의 토큰 및 유저 정보를 가져옵니다.
     */
    Mono<OAuthUserInfoResult> getUserInfo(String code, OAuthProvider provider);

    /**
     * 소셜 서비스와 우리 앱의 연동을 해제합니다.
     */
    Mono<Boolean> unlink(OAuthProvider provider, String oauthId, String oauthRefreshToken);

    /**
     * 소셜 리프레시 토큰을 사용하여 새로운 소셜 토큰 세트를 발급받습니다.
     */
    Mono<SocialTokenRefreshResult> refreshSocialToken(OAuthProvider provider, String refreshToken);

    // 이 어댑터가 해당 provider를 지원하는지 확인
    boolean supports(OAuthProvider provider);

    /**
     * 각 소셜 플랫폼의 로그인 페이지 URL을 반환합니다.
     */
    String getLoginUrl();
}