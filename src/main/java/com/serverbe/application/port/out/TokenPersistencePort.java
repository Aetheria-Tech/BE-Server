package com.serverbe.application.port.out;

import java.time.Duration;

public interface TokenPersistencePort {
    // 리프레시 토큰 등록
    void saveRefreshToken(Long userId, String refreshToken, Duration expiry);

    // 리프레시 토큰 삭제
    void deleteRefreshToken(Long userId);

    // 액세스 토큰 블랙리스트 등록 (만료 시간만큼 저장)
    void blacklistAccessToken(String accessToken, Duration remainingTime);

    // 블랙리스트 여부 확인
    boolean isBlacklisted(String accessToken);
}