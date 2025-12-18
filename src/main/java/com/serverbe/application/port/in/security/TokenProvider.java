package com.serverbe.application.port.in.security;

import com.serverbe.application.port.in.dto.AccessTokenResponse;
import com.serverbe.application.port.in.dto.RefreshTokenResponse;
import com.serverbe.domain.model.vo.Role;

public interface TokenProvider {
    AccessTokenResponse generateAccessToken(Long id, Role role);
    RefreshTokenResponse generateRefreshToken(Long id, Role role);
}
