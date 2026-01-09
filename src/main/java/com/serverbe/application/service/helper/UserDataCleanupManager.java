package com.serverbe.application.service.helper;

import com.serverbe.application.port.out.jpa.RunningArtRepositoryPort;
import com.serverbe.application.port.out.jpa.UserRepositoryPort;
import com.serverbe.application.port.out.token.TokenPersistencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserDataCleanupManager {

    private final UserRepositoryPort userRepositoryPort;
    private final RunningArtRepositoryPort runningArtRepositoryPort;
    private final TokenPersistencePort tokenPersistencePort;

    @Transactional
    public void deleteAllUserData(Long userId) {
        runningArtRepositoryPort.deleteByUserId(userId);
        userRepositoryPort.deleteById(userId);
        tokenPersistencePort.deleteRefreshToken(userId);
    }
}