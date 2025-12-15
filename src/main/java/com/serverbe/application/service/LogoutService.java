package com.serverbe.application.service;


import com.serverbe.application.port.in.oauth.LogoutUseCase;
import com.serverbe.application.port.out.TokenPersistencePort;
import com.serverbe.infrastructure.security.JwtTokenResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LogoutService implements LogoutUseCase {

    private final TokenPersistencePort tokenPersistencePort;
    private final JwtTokenResolver jwtTokenResolver;

    @Override
    public void logout(String accessToken) {
        // 1. 토큰에서 사용자 ID 추출
        Long userId = jwtTokenResolver.getIdFromToken(accessToken);

        // 2. Redis에서 리프레시 토큰 삭제 (더 이상 재발급 불가)
        tokenPersistencePort.deleteRefreshToken(userId);

        // 3. 액세스 토큰의 남은 유효 시간 계산
        Instant expiration = jwtTokenResolver.getExpirationFromToken(accessToken);
        Duration remainingTime = Duration.between(Instant.now(), expiration);

        // 4. 액세스 토큰 블랙리스트 등록 (남은 시간 동안만)
        if (!remainingTime.isNegative()) {
            tokenPersistencePort.blacklistAccessToken(accessToken, remainingTime);
        }
    }
}