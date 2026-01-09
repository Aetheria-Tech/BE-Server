package com.serverbe.application.service;

import com.serverbe.application.port.in.oauth.LogoutUseCase;
import com.serverbe.application.port.out.security.TokenResolver;
import com.serverbe.application.port.out.token.TokenPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * @author Duskafka
 * @responsibility 사용자의 로그아웃 요청을 처리하여 현재 세션 또는 모든 기기의 세션을 무효화하고, 사용된 토큰을 차단합니다.
 * @implSpec {@link LogoutUseCase} 인터페이스의 구현체로, 토큰 무효화 정책(Blacklist)을 관리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutService implements LogoutUseCase {

    private final TokenPersistencePort tokenPersistencePort;
    private final TokenResolver tokenResolver;

    /**
     * @param accessToken  로그아웃에 사용된 액세스 토큰
     * @param refreshToken 무효화할 특정 리프레시 토큰
     * @responsibility 요청된 특정 기기의 세션을 종료하고 액세스 토큰을 블랙리스트에 등록합니다.
     * @requirement UC-AUTH-02: 현재 기기 로그아웃
     * @implSpec 1. {@link TokenResolver}를 통해 토큰 소유자를 식별합니다.<br>
     * 2. 요청된 특정 리프레시 토큰을 삭제하여 재발급을 방지합니다.<br>
     * 3. 액세스 토큰의 남은 수명만큼 블랙리스트에 등록하여 재사용을 차단합니다.
     */
    @Override
    public void logout(String accessToken, String refreshToken) {
        Long userId = tokenResolver.getIdFromToken(accessToken);

        // 특정 리프레시 토큰 삭제
        tokenPersistencePort.removeSpecificRefreshToken(userId, refreshToken);

        // 액세스 토큰 차단
        handleTokenBlacklist(accessToken, userId, "현재 기기");
    }

    /**
     * @param accessToken 로그아웃에 사용된 액세스 토큰
     * @responsibility 해당 사용자의 모든 활성 세션을 일괄 종료하고 현재 토큰을 무효화합니다.
     * @requirement UC-AUTH-03: 모든 기기 로그아웃
     * @implSpec 1. 저장소 내 사용자와 관련된 모든 리프레시 토큰을 제거합니다.<br>
     * 2. 현재 사용 중인 액세스 토큰을 블랙리스트에 등록합니다.
     * @implNote 계정 탈취 의심이나 보안 강화가 필요한 시나리오에서 호출됩니다.
     */
    @Override
    public void globalLogout(String accessToken) {
        Long userId = tokenResolver.getIdFromToken(accessToken);

        // 모든 리프레시 토큰 삭제
        tokenPersistencePort.deleteRefreshToken(userId);

        // 액세스 토큰 차단
        handleTokenBlacklist(accessToken, userId, "모든 기기");
        log.warn("[SECURITY ALERT] 사용자(ID: {})의 모든 세션이 강제 종료되었습니다.", userId);
    }

    /**
     * @param accessToken 차단할 토큰
     * @param userId      유저 식별자
     * @param type        로그아웃 유형 (메시지용)
     * @responsibility 액세스 토큰을 블랙리스트에 등록하고 관련 로그를 남깁니다.
     */
    private void handleTokenBlacklist(String accessToken, Long userId, String type) {
        Duration remainingTime = calculateRemainingDuration(accessToken);

        if (!remainingTime.isNegative()) {
            tokenPersistencePort.blacklistAccessToken(accessToken, remainingTime);
            log.info("[LOGOUT] {} 로그아웃 완료. 사용자 ID: {}, 차단 기간: {}초",
                    type, userId, remainingTime.getSeconds());
        }
    }

    /**
     * @param accessToken 유효 기간을 확인할 토큰
     * @return 현재 시각 기준 남은 {@link Duration}
     * @responsibility 액세스 토큰의 만료 시각과 현재 시각의 차이를 계산하여 남은 수명(TTL)을 반환합니다.
     */
    private Duration calculateRemainingDuration(String accessToken) {
        Instant expiration = tokenResolver.getExpirationFromToken(accessToken);
        return Duration.between(Instant.now(), expiration);
    }
}