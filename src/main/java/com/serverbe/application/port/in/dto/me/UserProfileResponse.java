package com.serverbe.application.port.in.dto.me;

import com.serverbe.domain.model.User;

public record UserProfileResponse(
    String email,
    String nickname,
    String statusMessage
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(user.email(), user.nickname(), user.statusMessage());
    }
}