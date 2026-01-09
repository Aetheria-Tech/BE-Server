package com.serverbe.application.service;

import com.serverbe.application.port.out.dto.oauth.TokenResult;
import com.serverbe.application.port.in.token.ReissueUseCase;
import com.serverbe.application.port.out.security.TokenProvider;
import com.serverbe.application.port.out.security.TokenResolver;
import com.serverbe.application.port.out.token.TokenPersistencePort;
import com.serverbe.domain.model.user.vo.Role;
import com.serverbe.infrastructure.config.properties.JwtProperties;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * @author Duskafka
 * @responsibility 만료된 액세스 토큰과 유효한 리프레시 토큰을 대조하여 새로운 토큰 세트(Access/Refresh)를 재발급하는 비즈니스 로직을 수행합니다.
 * @implSpec 1. <b>RTR (Refresh Token Rotation)</b>: 리프레시 토큰을 1회용으로 제한하여 탈취된 토큰의 재사용을 방지합니다.<br>
 * 2. <b>Security</b>: 토큰 재사용 감지 시 해당 사용자의 모든 리프레시 토큰을 무효화하여 계정 보안을 강화합니다.
 */
@Slf4j
@Service
public class ReissueService implements ReissueUseCase {

    private final TokenPersistencePort tokenPersistencePort;
    private final TokenProvider tokenProvider;
    private final TokenResolver tokenResolver;
    private final Duration refreshTokenExpirationDays;

    /**
     * @param jwtProperties JWT 정책 설정 정보를 담은 객체
     * @responsibility 토큰 처리 인프라와 만료 정책을 주입받아 서비스를 초기화합니다.
     */
    public ReissueService(
            TokenPersistencePort tokenPersistencePort,
            TokenProvider tokenProvider,
            TokenResolver tokenResolver,
            JwtProperties jwtProperties
    ) {
        this.tokenPersistencePort = tokenPersistencePort;
        this.tokenProvider = tokenProvider;
        this.tokenResolver = tokenResolver;
        this.refreshTokenExpirationDays = jwtProperties.refreshToken().expirationDays();
    }

    /**
     * @param accessToken  만료된 액세스 토큰
     * @param refreshToken 현재 클라이언트가 보유한 리프레시 토큰
     * @return 신규 발급된 {@link TokenResult}
     * @throws BusinessException 토큰이 존재하지 않거나, 이미 사용된(재사용 감지) 토큰인 경우 발생
     * @responsibility 만료된 액세스 토큰의 정보를 기반으로 유효한 리프레시 토큰 확인 후 새로운 토큰 쌍을 발행합니다.
     * @requirement <b>UC-TKN-01: 토큰 재발급</b>
     * @implSpec 1. 리프레시 토큰의 구조적 유효성을 검증합니다.<br>
     * 2. 액세스 토큰(만료된 상태 포함)에서 유저 식별자와 권한 정보를 추출합니다.<br>
     * 3. Redis 저장소 내 리프레시 토큰 존재 여부를 통해 재사용을 감지합니다.<br>
     * 4. 기존 토큰을 폐기(Delete)하고 신규 토큰을 저장(Save)하는 RTR 주기를 완성합니다.
     * @implNote 리프레시 토큰 재사용이 감지되면 보안 위협으로 간주하여 유저의 모든 활성 세션을 강제 종료(Redis 내 전체 삭제)합니다.
     */
    @Override
    @Transactional
    public TokenResult reissue(String accessToken, String refreshToken) {
        // 1. 형식 검증
        checkRefreshTokenValid(refreshToken);

        // 2. 만료 토큰에서 정보 추출 (TokenResolver의 ExpiredJwtException 처리 활용)
        Long userId = tokenResolver.getIdFromToken(accessToken);
        Role role = tokenResolver.getRoleFromToken(accessToken);

        // 3. 저장소 대조 및 재사용 감지
        checkRefreshTokenExistInRedis(userId, refreshToken);

        // 4. 신규 토큰 생성
        TokenResult newTokens = generateNewTokens(userId, role);

        // 5. RTR (기존 토큰 삭제 및 신규 등록)
        deleteAndSaveRefreshToken(userId, refreshToken, newTokens.refreshTokenResult().opaqueToken());

        return newTokens;
    }

    /**
     * @throws BusinessException 리프레시 토큰 형식이 잘못된 경우 (400)
     * @responsibility 리프레시 토큰의 문자열 형식이 유효한지(길이 등) 확인합니다.
     */
    private void checkRefreshTokenValid(String refreshToken) {
        if (!tokenResolver.validateRefreshToken(refreshToken)) {
            log.warn("[보안] 유효하지 않은 형식의 리프레시 토큰으로 재발급 시도가 감지되었습니다.");
            throw new BusinessException(ErrorMessage.REFRESH_TOKEN_NOT_EXIST);
        }
    }

    /**
     * @param userId       유저 식별자
     * @param refreshToken 검증할 리프레시 토큰
     * @responsibility Redis 저장소에 해당 유저의 리프레시 토큰이 존재하는지 확인합니다.
     * @implNote 토큰이 존재하지 않는 경우, 이미 사용된 토큰을 이용한 <b>Replay Attack</b>으로 간주하고 유저의 모든 세션을 삭제합니다.
     */
    private void checkRefreshTokenExistInRedis(Long userId, String refreshToken) {
        if (!tokenPersistencePort.existsRefreshToken(userId, refreshToken)) {

            log.warn("[SECURITY ALERT] 리프레시 토큰 재사용이 감지되었습니다. 토큰 탈취 의심으로 인해 해당 사용자의 모든 세션을 강제 종료합니다. 사용자 ID: {}, 토큰 일부: {}...",
                    userId, refreshToken.substring(0, 10));

            // 보안 조치: 탈취 의심으로 인한 모든 세션 강제 종료
            tokenPersistencePort.deleteRefreshToken(userId);
            throw new BusinessException(
                    ErrorMessage.INVALID_REFRESH_TOKEN,
                    "이미 사용되었거나 유효하지 않은 토큰입니다."
            );
        }
    }

    /**
     * @responsibility 유저의 식별자와 권한 정보를 기반으로 새로운 Access/Refresh 토큰 쌍을 생성합니다.
     */
    private TokenResult generateNewTokens(Long userId, Role role) {
        return TokenResult.of(
                tokenProvider.generateAccessToken(userId, role),
                tokenProvider.generateRefreshToken(userId, role),
                role
        );
    }

    /**
     * @param userId          유저 식별자
     * @param oldRefreshToken 삭제할 기존 토큰
     * @param newRefreshToken 저장할 신규 토큰
     * @responsibility <b>RTR 전략의 핵심</b> 단계로, 사용된 이전 리프레시 토큰을 삭제하고 새로 발급된 토큰을 저장합니다.
     */
    private void deleteAndSaveRefreshToken(Long userId, String oldRefreshToken, String newRefreshToken) {
        tokenPersistencePort.removeSpecificRefreshToken(userId, oldRefreshToken);

        tokenPersistencePort.saveRefreshToken(
                userId,
                newRefreshToken,
                refreshTokenExpirationDays
        );
    }
}