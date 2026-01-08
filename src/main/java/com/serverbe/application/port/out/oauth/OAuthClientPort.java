package com.serverbe.application.port.out.oauth;


import com.serverbe.application.port.out.dto.oauth.OAuthUserInfoResult;
import com.serverbe.application.port.out.dto.oauth.SocialTokenRefreshResult;
import com.serverbe.domain.model.user.vo.OAuthProvider;
import reactor.core.publisher.Mono;

public interface OAuthClientPort {
    /**
     * 인가 코드로 해당 플랫폼의 토큰 및 유저 정보를 가져옵니다.
     *
     * @param code     OAuth 서버에서 받아온 인가 코드
     * @param provider 사용자가 요청한 OAuth 서버로 {@link OAuthProvider}를 받는다.
     * @return {@link OAuthUserInfoResult}로 사용자의 정보를 응답한다.
     * @responsibility 로그인이 성공한 사용자의 OAuth 코드로 사용자 정보를 받아온다.
     */
    Mono<OAuthUserInfoResult> getUserInfo(String code, OAuthProvider provider);

    /**
     * 소셜 서비스와 우리 앱의 연동을 해제합니다.
     *
     * @param provider          사용자가 사용사는 통신 방법
     * @param oauthId           사용자의 OAuthID
     * @param oauthRefreshToken 사용자의 OAuth 리프레시 토큰 (Google은 리프레시 토큰을 사용한다)
     * @return {@code Boolean}으로 회원 탈퇴에 성공했다면 {@code True}, 실패했다면 {@code False}를 응답한다
     * @implSpec 외부 서버와 통신하기 때문에 리액티브 스트림으로 응답한다.
     * @responsibility 사용자 회원 탈퇴를 수행한다.
     */
    Mono<Boolean> unlink(OAuthProvider provider, String oauthId, String oauthRefreshToken);

    /**
     * 소셜 리프레시 토큰을 사용하여 새로운 소셜 토큰 세트를 발급받습니다.
     */
    Mono<SocialTokenRefreshResult> refreshSocialToken(OAuthProvider provider, String refreshToken);

    /**
     * 이 어댑터가 해당 provider를 지원하는지 확인
     *
     * @param provider 사용자가 사용하는 OAuth 서버
     * @return 이 어댑터를 사용해야 한다면 true, 아니라면 false
     * @responsibility 만약 {@link OAuthProvider}가 {@code GOOGLE}이라면 이 어댑터를 사용할 수 있도록 한다.
     */
    boolean supports(OAuthProvider provider);

    /**
     * 소셜 플랫폼의 로그인 페이지 URL을 반환합니다.
     *
     * @return 로그인을 수행할 수 있는 URL
     * @responsibility 로그인을 수행할 수 있는 URL을 응답한다.
     */
    String getLoginUrl();
}