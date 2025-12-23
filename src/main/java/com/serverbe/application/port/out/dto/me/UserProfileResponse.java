package com.serverbe.application.port.out.dto.me;

import com.serverbe.domain.model.user.User;

public record UserProfileResponse(
    String email,
    String nickname,
    String statusMessage
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(user.email(), user.nickname(), user.statusMessage());
    }
}