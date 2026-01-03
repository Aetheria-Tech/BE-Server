package com.serverbe.application.port.in.me;


import com.serverbe.application.port.out.dto.me.UserProfileResult;

public interface GetUserUseCase {
    UserProfileResult getMyProfile(Long userId);
}