package com.serverbe.application.port.in.me;

import com.serverbe.application.port.out.dto.me.UserProfileResult;
import com.serverbe.application.port.out.dto.me.UserUpdateCommand;

public interface UpdateUserUseCase {
    UserProfileResult updateMyProfile(Long userId, UserUpdateCommand command);
}