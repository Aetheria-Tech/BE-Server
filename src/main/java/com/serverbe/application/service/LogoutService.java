package com.serverbe.application.service;


import com.serverbe.application.port.in.oauth.LogoutUseCase;
import com.serverbe.application.port.out.security.TokenResolver;
import com.serverbe.application.port.out.token.TokenPersistencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * @author Duskafka
 * @responsiblity 로그아웃을 수행하는 책임
 * @see LogoutUseCase
 */
@Service
@RequiredArgsConstructor
public class LogoutService implements LogoutUseCase {

    private final TokenPersistencePort tokenPersistencePort;
    private final TokenResolver tokenResolver;

    /**
     * @param accessToken  사용자가 로그아웃에 사용한 액세스 토큰
     * @param refreshToken 사용자가 로그아웃을 요청한 리프레시 토큰
     * @FR UC-AUTH-02 현재 기기 로그아웃
     * @responsiblity 단일 기기(현재 기기)에서 로그아웃을 시키는 책임
     * @implNote Redis에 접근하여 저장된 리프레시 토큰을 무효화하고 액세스 토큰을 블랙리스트에 등록한다.
     * @see LogoutUseCase#logout(String, String)
     */
    @Override
    public void logout(String accessToken, String refreshToken) {
        // 1. 토큰에서 사용자 ID 추출
        Long userId = tokenResolver.getIdFromToken(accessToken);

        // 2. Redis에서 리프레시 토큰 삭제 (더 이상 재발급 불가)
        tokenPersistencePort.removeSpecificRefreshToken(userId, refreshToken);

        // 3. 액세스 토큰의 남은 유효 시간 계산
        Instant expiration = tokenResolver.getExpirationFromToken(accessToken);
        Duration remainingTime = Duration.between(Instant.now(), expiration);

        // 4. 액세스 토큰 블랙리스트 등록 (남은 시간 동안만)
        if (!remainingTime.isNegative()) {
            tokenPersistencePort.blacklistAccessToken(accessToken, remainingTime);
        }
    }

    /**
     * @param accessToken 사용자가 로그아웃에 사용한 액세스 토큰
     * @FR UC-AUTH-03 모든 기기 로그아웃
     * @responsiblity 모든 기기에서 로그아웃을 시키는 책임
     * @implNote Redis에 접근하여 사용자의 ID로 등록된 리프레시 토큰을 모두 무효하고 액세스 토큰을 블랙리스트에 등록시킨다.
     * @see LogoutUseCase#globalLogout(String)
     */
    @Override
    public void globalLogout(String accessToken) {
        // 1. 토큰에서 사용자 ID 추출
        Long userId = tokenResolver.getIdFromToken(accessToken);

        // 2. 모든 리프레쉬 토큰 삭제
        tokenPersistencePort.deleteRefreshToken(userId);

        // 3. 액세스 토큰의 남은 유효 시간 계산
        Instant expiration = tokenResolver.getExpirationFromToken(accessToken);
        Duration remainingTime = Duration.between(Instant.now(), expiration);

        // 4. 액세스 토큰 블랙리스트 등록 (남은 시간 동안만)
        if (!remainingTime.isNegative()) {
            tokenPersistencePort.blacklistAccessToken(accessToken, remainingTime);
        }
    }
}