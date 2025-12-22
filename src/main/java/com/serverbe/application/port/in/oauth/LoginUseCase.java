package com.serverbe.application.port.in.oauth;


import com.serverbe.application.port.out.dto.oauth.TokenResponse;
import com.serverbe.domain.model.user.vo.OAuthProvider;
import reactor.core.publisher.Mono;

public interface LoginUseCase {
    /**
     * @param code OAuth 제공자로부터 받은 인가 코드
     * @param provider KAKAO, GOOGLE 등
     * @return 우리 서버가 발급한 JWT 토큰 세트
     */
    Mono<TokenResponse> login(String code, OAuthProvider provider);
    String getSocialLoginUrl(OAuthProvider provider);
}