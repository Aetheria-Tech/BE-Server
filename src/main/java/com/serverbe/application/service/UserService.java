package com.serverbe.application.service;

import com.serverbe.application.port.out.dto.me.UserProfileResponse;
import com.serverbe.application.port.out.dto.me.UserUpdateCommand;
import com.serverbe.application.port.in.me.UserUseCase;
import com.serverbe.application.port.out.jpa.UserRepositoryPort;
import com.serverbe.domain.model.user.User;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService implements UserUseCase {

    private final UserRepositoryPort userRepositoryPort;

    @Override
    public UserProfileResponse getMyProfile(Long userId) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorMessage.NOT_FOUND_USER));
        return UserProfileResponse.from(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateMyProfile(Long userId, UserUpdateCommand command) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorMessage.NOT_FOUND_USER));

        // 도메인 모델의 비즈니스 로직 호출 후 저장
        User updatedUser = user.updateProfile(
                command.nickname(),
                command.statusMessage()
        );

        userRepositoryPort.save(updatedUser);
        return UserProfileResponse.from(updatedUser);
    }
}