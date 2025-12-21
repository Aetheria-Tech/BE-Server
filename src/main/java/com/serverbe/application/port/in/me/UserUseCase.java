package com.serverbe.application.port.in.me;


import com.serverbe.application.port.out.dto.me.UserProfileResponse;
import com.serverbe.application.port.out.dto.me.UserUpdateCommand;

public interface UserUseCase {
    UserProfileResponse getMyProfile(Long userId);
    UserProfileResponse updateMyProfile(Long userId, UserUpdateCommand command);
}