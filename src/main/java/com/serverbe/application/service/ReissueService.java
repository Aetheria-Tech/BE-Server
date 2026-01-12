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
     * @param refreshToken 현재 클라이언트가 보유한 리프레시 토큰
     * @return 신규 발급된 액세스/리프레시 토큰 쌍 {@link TokenResult}
     * @throws BusinessException - REFRESH_TOKEN_NOT_EXIST: 토큰 형식이 잘못된 경우<br>
     *                           - INVALID_REFRESH_TOKEN: 저장소에 토큰이 없거나 이미 사용된 경우 (재사용 감지)
     * @requirement <b>UC-TKN-01: 토큰 재발급 및 세션 갱신</b>
     * @responsibility 만료된 액세스 토큰에서 유저 정보를 복구하고, 리프레시 토큰의 생존 여부를 대조하여 안전하게 세션을 연장합니다.
     */
    @Override
    public TokenResult reissue(String accessToken, String refreshToken) {
        // 1. 리프레시 토큰의 구조적 유효성(형식, 서명 등) 선검증
        validateTokenFormat(refreshToken);

        // 2. 만료된 액세스 토큰에서 유저 식별자 및 권한 정보 복구
        // TokenResolver는 내부적으로 ExpiredJwtException 발생 시에도 클레임을 파싱하도록 설계되어야 함
        Long userId = tokenResolver.getIdFromToken(accessToken);
        Role role = tokenResolver.getRoleFromToken(accessToken);

        // 3. Redis 저장소 내 실시간 세션 존재 여부 확인 및 재사용 감지 처리
        validateSessionAndHandleReplay(userId, refreshToken);

        // 4. 신규 보안 토큰 생성 (JWT)
        TokenResult newTokens = generateNewTokens(userId, role);

        // 5. RTR 주기를 완성: 기존 토큰 무효화 및 신규 토큰 세션 등록
        authSessionManager.rotateSession(userId, refreshToken, newTokens.refreshTokenResult().opaqueToken());

        log.info("[REISSUE COMPLETE] 사용자 ID: {} 의 세션이 RTR 정책에 의해 갱신되었습니다.", userId);
        return newTokens;
    }

    /**
     * @param userId       유저 고유 식별자
     * @param refreshToken 검증 대상 리프레시 토큰
     * @responsibility 저장소 내 토큰 부재 시 이를 <b>'토큰 재사용(Reuse)'</b> 시나리오로 판단하고 보안 격리 조치를 취합니다.
     * @implNote 리프레시 토큰은 사용 직후 삭제되므로, 클라이언트가 유효한 기간 내에 토큰을 가졌음에도 저장소에 없다면
     * 이는 이미 다른 경로(해커 혹은 중복 요청)에 의해 사용되었음을 의미합니다.
     */
    private void validateSessionAndHandleReplay(Long userId, String refreshToken) {
        if (!authSessionManager.isSessionValid(userId, refreshToken)) {
            log.error("[SECURITY ALERT] 리프레시 토큰 재사용 발생! 유저 ID: {} 의 모든 세션을 강제 종료합니다.", userId);

            // [보안 조치] 해당 유저의 모든 기기에서 즉시 로그아웃 처리
            authSessionManager.terminateAllSessions(userId);

            throw new AuthException(
                    AuthErrorCode.REISSUE_FAILED,
                    "보안 위협이 감지되었거나 유효하지 않은 세션입니다. 다시 로그인해주세요."
            );
        }
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