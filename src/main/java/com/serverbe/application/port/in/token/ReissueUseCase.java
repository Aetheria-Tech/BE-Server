package com.serverbe.application.port.in.token;

import com.serverbe.application.port.out.dto.oauth.TokenResponse;

public interface ReissueUseCase {
    TokenResponse reissue(String accessToken, String refreshToken);
}