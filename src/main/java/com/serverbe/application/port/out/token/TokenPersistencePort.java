package com.serverbe.application.port.out.token;

import java.time.Duration;

public interface TokenPersistencePort {
    /**
     * Redis에 리프레시 토큰을 저장하기 위해서 사용한다.
     *
     * @param userId       사용자 ID
     * @param refreshToken 저장할 리프레시 토큰
     * @param expiry       리프레시 토큰의 유효기간 (TTL로 저장하기 위해서 사용)
     * @responsibility Redis에 리프레시 토큰을 저장하는 책임
     */
    void saveRefreshToken(Long userId, String refreshToken, Duration expiry);

    /**
     * 리프레시 토큰을 조회하기 위하여 사용한다.
     *
     * @param userId 사용자 ID
     * @return 조회한 가장 최신 토큰
     * @deprecated
     */
    String getRefreshToken(Long userId);

    /**
     * 사용자의 모든 리프레시 토큰을 삭제하는 메소드
     *
     * @param userId 사용자 ID
     * @implSpec Redis에서 사용자 ID와 일치하는 모든 리프레시 토큰을 무효화한다.
     * @responsibility Key값에 일치하는 모든 Value(리프레시 토큰)을 삭제하는 책임.
     */
    void deleteRefreshToken(Long userId);

    /**
     * 토큰을 새로 발급할 때 사용한 액세스 토큰을 Redis에 블랙리스트로 등록하는 메소드.
     *
     * @param accessToken   블랙리스트에 등록할 액세스 토큰
     * @param remainingTime 액세스 토큰의 남은 시간
     * @responsibility 액세스 토큰을 Redis의 블랙리스트에 등록한다.
     */
    void blacklistAccessToken(String accessToken, Duration remainingTime);

    /**
     * 블랙리스트에 등록되었는지 확인하기 위한 메소드.
     *
     * @param accessToken 블랙리스트에 등록되었는지 확인할 액세스 토큰
     * @return 블랙리스트에 등록되었으면 true, 아니면 false
     * @responsibility 액세스 토큰이 블랙리스트에 등록되었는지 확인하는 책임.
     */
    boolean isBlacklisted(String accessToken);

    /**
     * Redis에 리프레시 토큰이 존재하는지 확인하기 위하여 사용한다.
     *
     * @param refreshToken 존재하는지 확인할 리프레시 토큰
     * @param userId       사용자 ID
     * @return 존재하면 true 존재하지 않으면 false, 만약 일치하는 것이 없아도 false
     * @responsibility Redis에 사용자의 리프레시 토큰이 존재하는지 확인하는 책임
     */
    boolean existsRefreshToken(Long userId, String refreshToken);

    /**
     * 특정 리프레시 토큰을 삭제하기 위해 사용한다.
     *
     * @param refreshToken 삭제할 리프레시 토큰
     * @param userId       사용자 ID
     * @responsibility 특정한 토큰을 찾아서 삭제하는 책임
     */
    void removeSpecificRefreshToken(Long userId, String refreshToken);
}