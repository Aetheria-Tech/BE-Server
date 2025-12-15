package com.serverbe.application.port.in.me;


import com.serverbe.application.port.in.dto.UserProfileResponse;
import com.serverbe.application.port.in.dto.UserUpdateCommand;

public interface UserUseCase {
    UserProfileResponse getMyProfile(Long userId);
    UserProfileResponse updateMyProfile(Long userId, UserUpdateCommand command);
}