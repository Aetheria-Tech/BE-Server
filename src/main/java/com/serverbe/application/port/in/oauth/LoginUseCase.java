package com.serverbe.application.port.in.oauth;


import com.serverbe.application.port.out.dto.oauth.TokenResult;
import com.serverbe.domain.model.user.vo.OAuthProvider;
import reactor.core.publisher.Mono;

public interface LoginUseCase {
    /**
     * @requirement UC-AUTH-01 로그인 및 회원가입
     * @param code OAuth 제공자로부터 받은 인가 코드
     * @param deviceId 특정 기기 식별자
     * @param provider KAKAO, GOOGLE 등
     * @return 우리 서버가 발급한 JWT 토큰 세트
     */
    Mono<TokenResult> login(String code, OAuthProvider provider, String deviceId);

    /**
     * @param provider KAKAO 또는 GOOGLE 같은 로그인 방식을 식별할 수 있는 Enum
     * @return 로그인을 진행할 수 있는 URL을 응답한다.
     * @responsibility 각 로그인 방식(OAuth 서버)에 따라 다른 로그인 URL을 응답하는 책임을 가진 메소드
     */
    String getSocialLoginUrl(OAuthProvider provider);
}