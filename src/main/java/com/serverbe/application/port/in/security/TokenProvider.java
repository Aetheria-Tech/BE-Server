package com.serverbe.application.port.in.security;

import com.serverbe.application.port.in.dto.RefreshTokenIssueResult;
import com.serverbe.domain.model.vo.Role;

public interface TokenProvider {
    String generateAccessToken(Long id, Role role);
    RefreshTokenIssueResult generateRefreshToken(Long id, Role role);
}
