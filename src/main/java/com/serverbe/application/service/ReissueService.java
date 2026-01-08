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
 * @responsibility 토큰 재발급 사용사례를 구현함
 * @see ReissueUseCase
 */
@Service
public class ReissueService implements ReissueUseCase {

    private final TokenPersistencePort tokenPersistencePort;
    private final TokenProvider tokenProvider;
    private final TokenResolver tokenResolver;
    private final Duration REFRESH_TOKEN_EXPIRATION_DAYS;

    public ReissueService(TokenPersistencePort tokenPersistencePort, TokenProvider tokenProvider, TokenResolver tokenResolver, JwtProperties jwtProperties) {
        this.tokenPersistencePort = tokenPersistencePort;
        this.tokenProvider = tokenProvider;
        this.tokenResolver = tokenResolver;
        this.REFRESH_TOKEN_EXPIRATION_DAYS = jwtProperties.refreshToken().expirationDays();
    }

    /**
     * @param accessToken  값은 유효하지만 기간이 지난 액세스 토큰(Redis에 블랙리스트로 등록되어있으면 안 됨)
     * @param refreshToken 값이 유효한 리프레시 토큰(Redis에 등록되어 있어야 함)
     * @return 재발급된 토큰 번들
     * @throws BusinessException 이미 사용되었거나 유효하지 않은 토큰일 때 발생.
     * @requirement UC-TKN-01: 토큰 재발급
     * @responsibility 토큰을 재발급하는 책임
     * @implSpec 중간에 Redis에 접근하여 액세스 토큰을 블랙리스트 처리하고 기존 리프레시 토큰을 삭제하고 새로 등록함.
     * @see ReissueUseCase#reissue(String, String) 구현하는 유즈케이스
     */
    @Override
    @Transactional
    public TokenResult reissue(String accessToken, String refreshToken) {
        // 1. 리프레시 토큰 자체의 유효성 검증
        if (!tokenResolver.validateRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorMessage.REFRESH_TOKEN_NOT_EXIST);
        }

        Long userId = tokenResolver.getIdFromToken(accessToken);
        Role role = tokenResolver.getRoleFromToken(accessToken);

        // 2. [보안 핵심] 리스트 내 존재 여부 확인 (재사용 감지)
        // 리스트에 토큰이 없다는 것은 이미 사용되었거나(RTR), 만료되어 밀려난 토큰임
        if (!tokenPersistencePort.existsRefreshToken(userId, refreshToken)) {
            // 탈취된 토큰을 재사용하려는 시도로 간주하고 모든 세션 무효화 (보안 조치)
            tokenPersistencePort.deleteRefreshToken(userId);
            throw new BusinessException(ErrorMessage.INVALID_REFRESH_TOKEN, "이미 사용되었거나 유효하지 않은 토큰입니다.");
        }

        // 3. 새로운 토큰 한 쌍 생성
        AccessTokenResult newAccessToken = tokenProvider.generateAccessToken(userId, role);
        RefreshTokenResult newRefreshTokenResult = tokenProvider.generateRefreshToken(userId, role);

        // 4. [RTR 핵심] 기존 토큰은 제거하고 새 토큰 저장
        // 사용된 기존 토큰만 리스트에서 삭제
        tokenPersistencePort.removeSpecificRefreshToken(userId, refreshToken);

        // 새로운 리프레시 토큰을 리스트에 추가 (이때 MAX_TOKEN 정책이 어댑터에서 적용됨)
        tokenPersistencePort.saveRefreshToken(
                userId,
                newRefreshTokenResult.opaqueToken(),
                REFRESH_TOKEN_EXPIRATION_DAYS // Duration (60일)
        );

        return TokenResult.of(
                newAccessToken,
                newRefreshTokenResult,
                role
        );
    }
}