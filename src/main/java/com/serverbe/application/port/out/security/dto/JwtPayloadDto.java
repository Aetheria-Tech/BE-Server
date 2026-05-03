package com.serverbe.application.port.out.security.dto;

import com.serverbe.domain.model.user.vo.Role;

public record JwtPayloadDto(
        Long userId,
        Role role
) {
}