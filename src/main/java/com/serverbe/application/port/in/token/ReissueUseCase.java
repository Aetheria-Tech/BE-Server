package com.serverbe.application.port.in.token;

import com.serverbe.application.port.out.dto.oauth.TokenResult;

public interface ReissueUseCase {
    TokenResult reissue(String accessToken, String refreshToken);
}