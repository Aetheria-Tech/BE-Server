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
     * @param deviceId 리프레시 토큰 조회를 요청한 기기의 식별자
     * @param userId   리프레시 토큰 조회를 요청한 사용자의 식별자
     * @responsibility 특정 기기에 할당된 리프레시 토큰을 조회합니다.
     */
    String getRefreshToken(Long userId, String deviceId);

    /**
     * @param userId   리프레시 토큰 삭제를 요청한 사용자의 식별자
     * @param deviceId 리프레시 토큰 삭제를 요청한 기기의 식별자
     * @responsibility 특정 기기의 세션만 로그아웃 처리합니다.
     */
    void deleteRefreshToken(Long userId, String deviceId);

    /**
     * @param userId 모든 리프레시 토큰 삭제를 요청한 사용자의 식별자
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

    /**
     * @param accessToken   블랙리스트에 등록할 액세스 토큰
     * @param remainingTime 리프레시 토큰의 남은 시간(이는 곧 블랙리스트로 저장될 시간을 의미합니다.)
     * @responsibility 액세스 토큰을 블랙리스트 처리합니다.
     */
    void blacklistAccessToken(String accessToken, Duration remainingTime);

    /**
     * @param refreshToken  블랙리스트에 등록할 리프레시 토큰
     * @param remainingTime 리프레시 토큰의 남은 시간(이는 곧 블랙리스트로 저장될 시간을 의미합니다.)
     * @responsibility 리프레시 토큰을 블랙리스트 처리합니다.
     */
    void blacklistRefreshToken(String refreshToken, Duration remainingTime);


    /**
     * @param accessToken 블랙리스트에 등록되었는지 확인할 액세스 토큰
     * @return 블랙리스트 되었다면 true, 아니면 false
     * @responsibility Redis의 액세스 토큰 블랙리스트에 액세스 토큰이 블랙리스트 되어있는지 확인할 책임.
     */
    boolean isAccessTokenBlacklisted(String accessToken);

    /**
     * @param refreshToken 블랙리스트에 등록되었는지 확인할 리프레시 토큰
     * @return 블랙리스트 되었다면 true, 아니면 false
     * @responsibility Redis의 리프레시 토큰 블랙리스트에 리프레시 토큰이 블랙리스트 되어있는지 확인할 책임.
     */
    boolean isRefreshTokenBlacklisted(String refreshToken);

    /**
     * @responsibility 특정 기기의 리프레시 토큰이 저장소에 존재하고 일치하는지 검증합니다.
     */
    boolean existsRefreshToken(Long userId, String deviceId, String refreshToken);

    long getSessionTtl(Long userId);
}