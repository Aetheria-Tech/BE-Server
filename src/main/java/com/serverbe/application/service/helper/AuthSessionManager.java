package com.serverbe.application.service.helper;

import com.serverbe.application.port.out.token.TokenPersistencePort;
import com.serverbe.infrastructure.config.properties.JwtProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * @author Duskafka
 * @responsibility 시스템 인증 세션의 생명주기(발급, 검증, 무효화, 교체)를 영속성 계층(Redis)과 상호작용하여 관리합니다.
 * @implSpec 1. <b>Session Isolation</b>: 유저 식별자와 토큰 값을 기반으로 개별 세션을 격리하여 관리합니다.<br>
 * 2. <b>Security Policy</b>: 토큰 블랙리스트 및 RTR(Refresh Token Rotation) 정책의 기술적 실행을 담당합니다.
 */
@Slf4j
@Component
public class AuthSessionManager {

    private final TokenPersistencePort tokenPersistencePort;
    private final Duration refreshTokenExpirationDays;

    /**
     * @param tokenPersistencePort 토큰 저장을 위한 출력 포트
     * @param jwtProperties        JWT 설정 정보 (리프레시 토큰 만료 기간 추출용)
     */
    public AuthSessionManager(TokenPersistencePort tokenPersistencePort, JwtProperties jwtProperties) {
        this.tokenPersistencePort = tokenPersistencePort;
        this.refreshTokenExpirationDays = jwtProperties.refreshToken().expirationDays();
    }

    /**
     * @param userId       유저 고유 식별자
     * @param refreshToken 저장할 리프레시 토큰 (Opaque 또는 JWT)
     * @responsibility 유저의 새로운 보안 세션을 생성하고 지정된 TTL(만료 시간) 동안 유지합니다.
     */
    public void saveSession(Long userId, String refreshToken) {
        tokenPersistencePort.saveRefreshToken(userId, refreshToken, refreshTokenExpirationDays);
        log.info("[SESSION] 새로운 보안 세션을 저장했습니다. UserID: {}, TTL: {} days", userId, refreshTokenExpirationDays.toDays());
    }

    /**
     * @param userId       유저 고유 식별자
     * @param refreshToken 무효화할 특정 리프레시 토큰
     * @responsibility 요청된 특정 기기 또는 브라우저의 세션을 즉시 파기합니다.
     */
    public void terminateSession(Long userId, String refreshToken) {
        tokenPersistencePort.removeSpecificRefreshToken(userId, refreshToken);
        log.info("[SESSION] 특정 기기의 세션을 종료했습니다. UserID: {}", userId);
    }

    /**
     * @param userId 세션을 모두 종료할 유저 고유 식별자
     * @responsibility 해당 사용자와 연결된 모든 기기의 리프레시 토큰을 삭제하여 전체 로그아웃을 수행합니다.
     */
    public void terminateAllSessions(Long userId) {
        tokenPersistencePort.deleteRefreshToken(userId);
        log.warn("[SESSION] 사용자의 모든 기기 세션을 강제 종료했습니다. UserID: {}", userId);
    }

    /**
     * @param accessToken   블랙리스트에 등록할 액세스 토큰
     * @param remainingTime 토큰의 남은 유효 기간
     * @responsibility 로그아웃된 액세스 토큰을 블랙리스트에 등록하여 만료 전까지 재사용을 원천 차단합니다.
     */
    public void registerBlacklist(String accessToken, Duration remainingTime) {
        tokenPersistencePort.blacklistAccessToken(accessToken, remainingTime);
        log.info("[BLACKLIST] 액세스 토큰을 블랙리스트에 등록했습니다. 차단 유지 시간: {} seconds", remainingTime.getSeconds());
    }

    /**
     * @param userId       유저 고유 식별자
     * @param refreshToken 검증할 리프레시 토큰
     * @return 세션 존재 여부 (유효 시 {@code true})
     * @responsibility 저장소(Redis)에 해당 토큰이 실제로 존재하는지 확인하여 세션의 유효성을 검증합니다.
     */
    public boolean isSessionValid(Long userId, String refreshToken) {
        boolean isValid = tokenPersistencePort.existsRefreshToken(userId, refreshToken);
        log.debug("[SESSION] 세션 유효성을 확인했습니다. UserID: {}, Valid: {}", userId, isValid);
        return isValid;
    }

    /**
     * @param userId          유저 고유 식별자
     * @param oldRefreshToken 폐기할 기존 토큰
     * @param newRefreshToken 등록할 신규 토큰
     * @responsibility <b>RTR(Refresh Token Rotation)</b> 정책에 따라 기존 세션을 신규 세션으로 원자적으로 교체합니다.
     * @implNote 이 과정이 성공하면 기존 토큰은 더 이상 사용할 수 없으며, 재사용 시 보안 위협으로 간주됩니다.
     */
    public void rotateSession(Long userId, String oldRefreshToken, String newRefreshToken) {
        tokenPersistencePort.removeSpecificRefreshToken(userId, oldRefreshToken);
        tokenPersistencePort.saveRefreshToken(userId, newRefreshToken, refreshTokenExpirationDays);
        log.info("[SESSION] 리프레시 토큰 교체(RTR)를 완료했습니다. UserID: {}", userId);
    }
}