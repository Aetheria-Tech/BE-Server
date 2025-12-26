package com.serverbe.application.port.in.me;


import com.serverbe.application.port.out.dto.me.UserProfileResponse;

public interface GetUserUseCase {
    UserProfileResponse getMyProfile(Long userId);
}