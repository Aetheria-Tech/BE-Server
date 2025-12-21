package com.serverbe.application.port.in.redis;

import java.time.Duration;

public interface TokenPersistenceUseCase {
    // 리프레시 토큰 등록
    void saveRefreshToken(Long userId, String refreshToken, Duration expiry);

    // 리프레시 토큰 조회
    String getRefreshToken(Long userId);

    // 리프레시 토큰 삭제
    void deleteRefreshToken(Long userId);

    // 액세스 토큰 블랙리스트 등록 (만료 시간만큼 저장)
    void blacklistAccessToken(String accessToken, Duration remainingTime);

    // 블랙리스트 여부 확인
    boolean isBlacklisted(String accessToken);

    // 리프레시 토큰 존재 여부 조회
    boolean existsRefreshToken(Long userId, String refreshToken);

    // 기존 리프레시 토큰 제거
    void removeSpecificRefreshToken(Long userId, String refreshToken);
}