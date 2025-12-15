package com.serverbe.application.port.in.oauth;


import com.serverbe.application.port.in.dto.OAuthUserInfo;
import com.serverbe.domain.model.vo.OAuthProvider;

public interface OAuthClientPort {
    /**
     * 인가 코드로 해당 플랫폼의 토큰 및 유저 정보를 가져옵니다.
     */
    OAuthUserInfo getUserInfo(String code, OAuthProvider provider);
}