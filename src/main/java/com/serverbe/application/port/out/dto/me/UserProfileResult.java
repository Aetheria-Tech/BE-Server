package com.serverbe.application.port.out.dto.me;

import com.serverbe.domain.model.user.User;

public record UserProfileResult(
    String email,
    String nickname,
    String statusMessage
) {
    public static UserProfileResult from(User user) {
        return new UserProfileResult(user.email(), user.nickname(), user.statusMessage());
    }
}