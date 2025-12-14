package com.serverbe.application.port.in.security;

import com.serverbe.application.port.in.dto.RefreshTokenIssueResult;
import org.springframework.security.core.Authentication;

public interface TokenProvider {
    String generateAccessToken(Authentication authentication);
    RefreshTokenIssueResult generateRefreshToken(Authentication authentication);

}
