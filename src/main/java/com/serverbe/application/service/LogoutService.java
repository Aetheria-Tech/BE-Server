package com.serverbe.application.service;


import com.serverbe.application.port.in.oauth.LogoutUseCase;
import com.serverbe.application.port.out.security.TokenResolver;
import com.serverbe.application.port.in.redis.TokenPersistenceUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LogoutService implements LogoutUseCase {

    private final TokenPersistenceUseCase tokenPersistenceUseCase;
    private final TokenResolver tokenResolver;

    @Override
    public void logout(String accessToken, String refreshToken) {
        // 1. 토큰에서 사용자 ID 추출
        Long userId = tokenResolver.getIdFromToken(accessToken);

        // 2. Redis에서 리프레시 토큰 삭제 (더 이상 재발급 불가)
        tokenPersistenceUseCase.removeSpecificRefreshToken(userId, refreshToken);

        // 3. 액세스 토큰의 남은 유효 시간 계산
        Instant expiration = tokenResolver.getExpirationFromToken(accessToken);
        Duration remainingTime = Duration.between(Instant.now(), expiration);

        // 4. 액세스 토큰 블랙리스트 등록 (남은 시간 동안만)
        if (!remainingTime.isNegative()) {
            tokenPersistenceUseCase.blacklistAccessToken(accessToken, remainingTime);
        }
    }

    @Override
    public void globalLogout(String accessToken) {
        // 1. 토큰에서 사용자 ID 추출
        Long userId = tokenResolver.getIdFromToken(accessToken);

        // 2. 모든 리프레쉬 토큰 삭제
        tokenPersistenceUseCase.deleteRefreshToken(userId);

        // 3. 액세스 토큰의 남은 유효 시간 계산
        Instant expiration = tokenResolver.getExpirationFromToken(accessToken);
        Duration remainingTime = Duration.between(Instant.now(), expiration);

        // 4. 액세스 토큰 블랙리스트 등록 (남은 시간 동안만)
        if (!remainingTime.isNegative()) {
            tokenPersistenceUseCase.blacklistAccessToken(accessToken, remainingTime);
        }
    }
}