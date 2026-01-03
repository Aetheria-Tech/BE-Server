package com.serverbe.application.port.out.security;

import com.serverbe.application.port.out.dto.oauth.AccessTokenResult;
import com.serverbe.application.port.out.dto.oauth.RefreshTokenResult;
import com.serverbe.domain.model.user.vo.Role;

public interface TokenProvider {
    AccessTokenResult generateAccessToken(Long id, Role role);
    RefreshTokenResult generateRefreshToken(Long id, Role role);
}
