package com.serverbe.application.service;

import com.serverbe.application.port.out.dto.oauth.AccessTokenResult;
import com.serverbe.application.port.out.dto.oauth.RefreshTokenResult;
import com.serverbe.application.port.out.dto.oauth.TokenResult;
import com.serverbe.application.port.in.token.ReissueUseCase;
import com.serverbe.application.port.out.security.TokenProvider;
import com.serverbe.application.port.out.security.TokenResolver;
import com.serverbe.application.port.out.token.TokenPersistencePort;
import com.serverbe.domain.model.user.vo.Role;
import com.serverbe.infrastructure.config.properties.JwtProperties;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * @author Duskafka
 * @responsibility 만료된 액세스 토큰과 유효한 리프레시 토큰을 대조하여 새로운 토큰 세트를 재발급합니다.
 * @implSpec {@link ReissueUseCase}의 구현체이며, <b>RTR(Refresh Token Rotation)</b> 전략을 통해 보안성을 강화합니다.
 */
@Service
public class ReissueService implements ReissueUseCase {

    private final TokenPersistencePort tokenPersistencePort;
    private final TokenProvider tokenProvider;
    private final TokenResolver tokenResolver;
    private final Duration REFRESH_TOKEN_EXPIRATION_DAYS;

    /**
     * @implSpec {@link JwtProperties}로부터 설정을 읽어와 리프레시 토큰의 유효 기간을 초기화합니다.
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
        this.REFRESH_TOKEN_EXPIRATION_DAYS = jwtProperties.refreshToken().expirationDays();
    }

    /**
     * @requirement UC-TKN-01: 토큰 재발급
     * @responsibility 토큰의 유효성을 검증하고 RTR 정책에 따라 신규 토큰 쌍을 발행합니다.
     * @implSpec
     * 1. <b>리프레시 토큰 검증</b>: {@link TokenResolver}를 통해 토큰의 구조적 유효성을 확인합니다.<br>
     * 2. <b>재사용 감지</b>: 저장소에 토큰이 없을 경우 탈취 시도로 간주하여 해당 사용자의 모든 세션을 종료합니다.<br>
     * 3. <b>토큰 교체</b>: 기존 리프레시 토큰을 삭제하고 신규 토큰 세트를 생성하여 저장합니다.
     * @implNote 리프레시 토큰은 일회용(One-time use)으로 관리됩니다.
     * @param accessToken  만료된 액세스 토큰
     * @param refreshToken 현재 유효한 리프레시 토큰
     * @return 재발급된 토큰 묶음 {@link TokenResult}
     * @throws BusinessException 토큰이 유효하지 않거나, 이미 사용된 토큰으로 확인될 경우 발생
     * @see ReissueUseCase#reissue(String, String)
     */
    @Override
    @Transactional
    public TokenResult reissue(String accessToken, String refreshToken) {
        if (!tokenResolver.validateRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorMessage.REFRESH_TOKEN_NOT_EXIST);
        }

        Long userId = tokenResolver.getIdFromToken(accessToken);
        Role role = tokenResolver.getRoleFromToken(accessToken);

        if (!tokenPersistencePort.existsRefreshToken(userId, refreshToken)) {
            tokenPersistencePort.deleteRefreshToken(userId);
            throw new BusinessException(ErrorMessage.INVALID_REFRESH_TOKEN, "이미 사용되었거나 유효하지 않은 토큰입니다.");
        }

        AccessTokenResult newAccessToken = tokenProvider.generateAccessToken(userId, role);
        RefreshTokenResult newRefreshTokenResult = tokenProvider.generateRefreshToken(userId, role);

        tokenPersistencePort.removeSpecificRefreshToken(userId, refreshToken);

        tokenPersistencePort.saveRefreshToken(
                userId,
                newRefreshTokenResult.opaqueToken(),
                REFRESH_TOKEN_EXPIRATION_DAYS
        );

        return TokenResult.of(
                newAccessToken,
                newRefreshTokenResult,
                role
        );
    }
}