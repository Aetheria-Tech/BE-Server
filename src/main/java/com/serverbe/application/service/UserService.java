package com.serverbe.application.service;

import com.serverbe.application.port.in.me.UpdateUserUseCase;
import com.serverbe.application.port.out.dto.me.UserProfileResult;
import com.serverbe.application.port.out.dto.me.UserUpdateCommand;
import com.serverbe.application.port.in.me.GetUserUseCase;
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
public class UserService implements GetUserUseCase, UpdateUserUseCase {

    private final UserRepositoryPort userRepositoryPort;

    @Override
    public UserProfileResult getMyProfile(Long userId) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorMessage.NOT_FOUND_USER));

        if (user.shouldBeRefreshed()) {
            user = userRepositoryPort.save(user); // 저장 시점에 최신 키로 암호화됨
        }

        return UserProfileResult.from(user);
    }

    @Override
    @Transactional
    public UserProfileResult updateMyProfile(Long userId, UserUpdateCommand command) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorMessage.NOT_FOUND_USER));

        // 도메인 모델의 비즈니스 로직 호출 후 저장
        User updatedUser = user.updateProfile(
                command.nickname(),
                command.statusMessage()
        );

        userRepositoryPort.save(updatedUser);
        return UserProfileResult.from(updatedUser);
    }
}