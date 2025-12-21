package com.serverbe.application.port.out.security;

import com.serverbe.application.port.out.dto.oauth.AccessTokenResponse;
import com.serverbe.application.port.out.dto.oauth.RefreshTokenResponse;
import com.serverbe.domain.model.vo.Role;

public interface TokenProvider {
    AccessTokenResponse generateAccessToken(Long id, Role role);
    RefreshTokenResponse generateRefreshToken(Long id, Role role);
}
