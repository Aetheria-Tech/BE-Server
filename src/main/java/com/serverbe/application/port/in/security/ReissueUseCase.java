package com.serverbe.application.port.in.security;

import com.serverbe.application.port.in.dto.oauth.TokenResponse;

public interface ReissueUseCase {
    TokenResponse reissue(String accessToken, String refreshToken);
}