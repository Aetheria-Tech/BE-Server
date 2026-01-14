package com.serverbe.application.service;

import com.serverbe.application.port.in.token.ReissueUseCase;
import com.serverbe.application.port.out.dto.oauth.TokenResult;
import com.serverbe.application.port.out.security.TokenProvider;
import com.serverbe.application.port.out.security.TokenResolver;
import com.serverbe.application.service.helper.AuthSessionManager;
import com.serverbe.domain.exception.auth.AuthErrorCode;
import com.serverbe.domain.exception.auth.AuthException;
import com.serverbe.domain.model.user.vo.Role;
import com.serverbe.domain.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * @author Duskafka
 * @responsibility 만료된 세션을 검증하고 보안 정책(RTR)에 따라 새로운 인증 토큰 세트를 발행하는 오케스트레이터입니다.
 * @implSpec 1. <b>RTR (Refresh Token Rotation)</b>: 발급된 리프레시 토큰은 단 1회만 사용 가능하며, 재발급 시 즉시 폐기됩니다.<br>
 * 2. <b>Replay Attack Detection</b>: 이미 사용되어 폐기된 리프레시 토큰으로 접근 시, 시스템은 이를 탈취 시도로 간주하고 모든 활성 세션을 즉시 차단합니다.<br>
 * 3. <b>Manager Pattern</b>: 세션의 상태 변화(저장, 삭제, 확인)는 {@link AuthSessionManager}를 통해 추상화하여 관리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReissueService implements ReissueUseCase {

    private final AuthSessionManager authSessionManager;
    private final TokenProvider tokenProvider;
    private final TokenResolver tokenResolver;

    /**
     * @param accessToken  만료된 액세스 토큰 (클레임 추출용)
     * @param deviceId     사용자의 기기 식별자
     * @param refreshToken 현재 클라이언트가 보유한 리프레시 토큰
     * @return 신규 발급된 액세스/리프레시 토큰 쌍 {@link TokenResult}
     * @throws BusinessException - REFRESH_TOKEN_NOT_EXIST: 토큰 형식이 잘못된 경우<br>
     *                           - INVALID_REFRESH_TOKEN: 저장소에 토큰이 없거나 이미 사용된 경우 (재사용 감지)
     * @requirement <b>UC-TKN-01: 토큰 재발급 및 세션 갱신</b>
     * @responsibility 만료된 액세스 토큰에서 유저 정보를 복구하고, 리프레시 토큰의 생존 여부를 대조하여 안전하게 세션을 연장합니다.
     */
    @Override
    @Transactional // Redis 작업의 원자성을 위해 붙여주는 것이 좋습니다 (PersistenceAdapter 내 multi/exec와 시너지)
    public TokenResult reissue(String accessToken, String refreshToken, String deviceId) {
        // 1. 형식 검증 및 정보 복구 (기존과 동일)
        Long userId = tokenResolver.getIdFromToken(accessToken);
        Role role = tokenResolver.getRoleFromToken(accessToken);

        // 2. 세션 검증 (블랙리스트 여부 + 현재 세션 일치 여부)
        validateSessionAndHandleReplay(userId, deviceId, refreshToken);

        // 3. 신규 토큰 생성
        TokenResult newTokens = generateNewTokens(userId, role);

        authSessionManager.rotateSession(
                userId,
                deviceId,
                refreshToken, // 기존 토큰 -> 블랙리스트로
                newTokens.refreshTokenResult().opaqueToken() // 새 토큰 -> 저장
        );

        log.info("[REISSUE SUCCESS] User: {}, Device: {}", userId, deviceId);
        return newTokens;
    }

    /**
     * @param userId       유저 고유 식별자
     * @param deviceId     기기 식별 정보
     * @param refreshToken 검증 대상 리프레시 토큰
     * @responsibility 저장소 내 토큰 부재 시 이를 <b>'토큰 재사용(Reuse)'</b> 시나리오로 판단하고 보안 격리 조치를 취합니다.
     * @implNote 리프레시 토큰은 사용 직후 삭제되므로, 클라이언트가 유효한 기간 내에 토큰을 가졌음에도 저장소에 없다면
     * 이는 이미 다른 경로(해커 혹은 중복 요청)에 의해 사용되었음을 의미합니다.
     */
    private void validateSessionAndHandleReplay(Long userId, String deviceId, String refreshToken) {
        // 1. 블랙리스트 확인: 이미 사용된 토큰으로 접근한 경우 (가장 위험한 상황)
        if (authSessionManager.isRefreshTokenBlacklisted(refreshToken)) {
            handleSecurityBreach(userId, "이미 블랙리스트에 등록된 토큰 사용 시도 (재사용 공격)");
        }

        // 2. 현재 세션과 일치 확인: 저장소의 토큰과 제출한 토큰이 다른 경우
        if (!authSessionManager.isSessionValid(userId, deviceId, refreshToken)) {
            handleSecurityBreach(userId, "현재 유효한 세션 토큰과 일치하지 않음 (비정상 접근)");
        }
    }

    /**
     * @responsibility 보안 침해 감지 시 즉시 전역 로그아웃 및 예외 발생
     */
    private void handleSecurityBreach(Long userId, String reason) {
        log.error("[SECURITY BREACH] 유저 ID: {} - 사유: {}", userId, reason);

        // 해당 유저의 모든 기기 세션 파괴
        authSessionManager.terminateAllSessions(userId);

        throw new AuthException(
                AuthErrorCode.REISSUE_FAILED,
                "보안 위협이 감지되어 모든 기기에서 로그아웃되었습니다. 다시 로그인해주세요."
        );
    }

    /**
     * @param refreshToken 검증할 문자열
     * @responsibility 토큰이 비어있지 않은지, 그리고 시스템이 정의한 JWT/Opaque 형식을 따르는지 확인합니다.
     */
    private void validateTokenFormat(String refreshToken) {
        if (!tokenResolver.validateRefreshToken(refreshToken)) {
            log.warn("[SECURITY ALERT] 부적절한 형식의 리프레시 토큰 접근.");
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_NOT_EXIST);
        }
    }

    /**
     * @param userId 유저 식별자
     * @param role   유저 권한
     * @return 신규 토큰 세트
     * @responsibility {@link TokenProvider}를 통해 표준 규격의 보안 토큰을 생성합니다.
     */
    private TokenResult generateNewTokens(Long userId, Role role) {
        return TokenResult.of(
                tokenProvider.generateAccessToken(userId, role),
                tokenProvider.generateRefreshToken(userId, role),
                role
        );
    }
}