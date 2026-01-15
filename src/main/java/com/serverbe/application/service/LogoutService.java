package com.serverbe.application.service;

import com.serverbe.application.port.in.oauth.LogoutUseCase;
import com.serverbe.application.port.out.security.TokenResolver;
import com.serverbe.application.service.helper.AuthSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * @author Duskafka
 * @responsibility 사용자의 로그아웃 요청에 따라 인증 세션을 무효화하고 사용된 토큰의 재사용을 방지합니다.
 * @implSpec {@link LogoutUseCase}의 구현체이며, {@link AuthSessionManager}를 통해 세션 저장소(Redis)의 상태를 관리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutService implements LogoutUseCase {

    private final AuthSessionManager authSessionManager;
    private final TokenResolver tokenResolver;

    /**
     * @param accessToken  로그아웃에 사용된 액세스 토큰
     * @param refreshToken 무효화할 특정 리프레시 토큰
     * @responsibility 현재 요청이 들어온 특정 기기의 세션만 선택적으로 종료하고 액세스 토큰을 차단합니다.
     * @requirement <b>UC-AUTH-02: 현재 기기 로그아웃</b>
     * @implSpec 1. {@link AuthSessionManager#terminateSession(Long, String)}을 호출하여 해당 리프레시 토큰을 무효화합니다.<br>
     * 2. 탈취된 토큰의 재사용을 방지하기 위해 사용된 액세스 토큰을 블랙리스트에 등록합니다.
     */
    @Override
    public void logout(String accessToken, String refreshToken, String deviceId) {
        Long userId = tokenResolver.getIdFromToken(accessToken);

        // 1. 리프레시 토큰 블랙리스트 등록 (최대 수명 적용)
        authSessionManager.blacklistRefreshToken(refreshToken);

        // 2.특정 기기 세션 삭제
        authSessionManager.terminateSession(userId, deviceId);

        // 3. 액세스 토큰 차단 (남은 수명 적용)
        handleTokenBlacklist(accessToken, userId, "현재 기기");
    }

    /**
     * @param accessToken 로그아웃에 사용된 액세스 토큰
     * @responsibility 해당 사용자와 연결된 모든 기기의 세션을 일괄 종료하여 보안 사고에 대응합니다.
     * @requirement <b>UC-AUTH-03: 모든 기기 로그아웃</b>
     * @implNote 계정 탈취 의심 시나리오나 비밀번호 변경 후 강제 로그아웃이 필요한 경우 활용됩니다.
     */
    @Override
    public void globalLogout(String accessToken) {
        Long userId = tokenResolver.getIdFromToken(accessToken);

        // 모든 리프레시 토큰 삭제 (전체 세션 종료)
        authSessionManager.terminateAllSessions(userId);

        // 현재 사용 중인 액세스 토큰 차단
        handleTokenBlacklist(accessToken, userId, "모든 기기");
        log.warn("[SECURITY ALERT] 사용자(ID: {})의 모든 세션이 일괄 종료되었습니다.", userId);
    }

    /**
     * @param accessToken 차단할 토큰
     * @param userId      유저 식별자
     * @param type        로그아웃 유형 (로그 기록용)
     * @responsibility 액세스 토큰의 남은 수명을 계산하여 블랙리스트에 등록함으로써 즉각적인 접근 차단을 보장합니다.
     */
    private void handleTokenBlacklist(String accessToken, Long userId, String type) {
        Duration remainingTime = calculateRemainingDuration(accessToken);

        if (!remainingTime.isNegative()) {
            authSessionManager.blacklistAccessToken(accessToken, remainingTime);
            log.info("[LOGOUT] {} 로그아웃 처리 완료. 사용자 ID: {}, 차단 유효 시간: {}초",
                    type, userId, remainingTime.getSeconds());
        }
    }

    /**
     * @param accessToken 기간을 계산할 토큰
     * @return 남은 시간 {@link Duration}
     * @responsibility 토큰의 만료 시각을 기반으로 현재 시각 기준 남은 유효 기간(TTL)을 산출합니다.
     */
    private Duration calculateRemainingDuration(String accessToken) {
        Instant expiration = tokenResolver.getExpirationFromToken(accessToken);
        return Duration.between(Instant.now(), expiration);
    }
}