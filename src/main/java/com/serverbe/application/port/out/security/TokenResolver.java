package com.serverbe.application.port.out.security;

import com.serverbe.domain.model.vo.Role;
import org.springframework.security.core.Authentication;

import java.time.Instant;

public interface TokenResolver {
    Authentication getAuthentication(String token);
    boolean validateAccessToken(String accessToken);
    boolean validateRefreshToken(String refreshToken);
    Long getIdFromToken(String token);
    Role getRoleFromToken(String token);
    Instant getExpirationFromToken(String token);
}
