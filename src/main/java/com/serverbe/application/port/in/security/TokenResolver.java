package com.serverbe.application.port.in.security;

import com.serverbe.domain.model.vo.Role;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;

public interface TokenResolver {
    Authentication getAuthentication(String token);
    boolean validateToken(String token);
    Long getIdFromToken(String token);
    Role getRoleFromToken(String token);
    Instant getExpirationFromToken(String token);
}
