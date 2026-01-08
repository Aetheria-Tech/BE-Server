package com.serverbe.application.port.in.oauth;


import com.serverbe.application.port.out.dto.oauth.TokenResult;
import com.serverbe.domain.model.user.vo.OAuthProvider;
import reactor.core.publisher.Mono;

public interface LoginUseCase {
    /**
     * @requirement UC-AUTH-01 로그인 및 회원가입
     * @param code OAuth 제공자로부터 받은 인가 코드
     * @param provider KAKAO, GOOGLE 등
     * @return 우리 서버가 발급한 JWT 토큰 세트
     */
    Mono<TokenResult> login(String code, OAuthProvider provider);
    String getSocialLoginUrl(OAuthProvider provider);
}