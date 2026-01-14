package com.serverbe.application.port.out.token;

import java.time.Duration;
import java.util.Set;

/**
 * @responsibility 멀티 디바이스 환경에서의 리프레시 토큰 관리 및 액세스 토큰 블랙리스트를 담당합니다.
 * Redis의 Sorted Set을 활용하여 세션의 순서(로그인 시간 등)를 관리하고 세션 개수 제한을 지원합니다.
 */
public interface TokenPersistencePort {

    /**
     * @param userId       사용자 식별자
     * @param deviceId     기기 식별자 (모바일, PC 등)
     * @param refreshToken 발급된 리프레시 토큰
     * @param expiry       토큰 유효 기간
     * @responsibility 특정 기기의 리프레시 토큰을 저장하고, 해당 사용자의 세션 목록(Index)을 업데이트합니다.
     */
    void saveRefreshToken(Long userId, String deviceId, String refreshToken, Duration expiry);

    /**
     * @responsibility 특정 기기에 할당된 리프레시 토큰을 조회합니다.
     */
    String getRefreshToken(Long userId, String deviceId);

    /**
     * @responsibility 특정 기기의 세션만 로그아웃 처리합니다.
     */
    void deleteRefreshToken(Long userId, String deviceId);

    /**
     * @responsibility 해당 사용자의 모든 기기에서 로그아웃 처리합니다. (전체 세션 비활성화)
     */
    void deleteAllRefreshTokens(Long userId);

    /**
     * @responsibility 특정 사용자의 현재 활성화된 모든 기기 식별자(deviceId) 목록을 조회합니다.
     * 가장 오래된 세션을 찾거나 세션 개수를 확인할 때 사용합니다.
     */
    Set<String> getAllDeviceIds(Long userId);

    /**
     * @param userId 사용자 식별자
     * @responsibility 사용자가 보유한 세션 중 가장 오래된(점수가 가장 낮은) 세션을 삭제합니다.
     */
    void removeOldestSession(Long userId);

    /**
     * @responsibility 현재 활성 세션 개수를 반환합니다.
     */
    long getSessionCount(Long userId);

    /**
     * @responsibility 신규 토큰 저장과 기존 토큰 블랙리스트 등록을 하나의 원자적 작업으로 수행합니다.
     */
    void rotateRefreshToken(Long userId, String deviceId, String oldRefreshToken, String newRefreshToken, Duration expiry);


    void blacklistAccessToken(String accessToken, Duration remainingTime);

    void blacklistRefreshToken(String refreshToken, Duration remainingTime);

    boolean isAccessTokenBlacklisted(String accessToken);

    boolean isRefreshTokenBlacklisted(String refreshToken);

    /**
     * @responsibility 특정 기기의 리프레시 토큰이 저장소에 존재하고 일치하는지 검증합니다.
     */
    boolean existsRefreshToken(Long userId, String deviceId, String refreshToken);
}