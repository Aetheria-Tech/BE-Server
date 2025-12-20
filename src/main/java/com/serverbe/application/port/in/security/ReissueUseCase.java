package com.serverbe.application.port.in.security;

import com.serverbe.application.port.in.dto.TokenResponse;

public interface ReissueUseCase {
    TokenResponse reissue(String accessToken, String refreshToken);
}