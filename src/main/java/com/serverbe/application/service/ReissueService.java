package com.serverbe.application.service;

import com.serverbe.application.port.out.dto.oauth.AccessTokenResponse;
import com.serverbe.application.port.out.dto.oauth.RefreshTokenResponse;
import com.serverbe.application.port.out.dto.oauth.TokenResponse;
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

@Service
@Transactional
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

    @Override
    public TokenResponse reissue(String accessToken, String refreshToken) {
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
        AccessTokenResponse newAccessToken = tokenProvider.generateAccessToken(userId, role);
        RefreshTokenResponse newRefreshTokenResult = tokenProvider.generateRefreshToken(userId, role);

        // 4. [RTR 핵심] 기존 토큰은 제거하고 새 토큰 저장
        // 사용된 기존 토큰만 리스트에서 삭제
        tokenPersistencePort.removeSpecificRefreshToken(userId, refreshToken);

        // 새로운 리프레시 토큰을 리스트에 추가 (이때 MAX_TOKEN 정책이 어댑터에서 적용됨)
        tokenPersistencePort.saveRefreshToken(
                userId,
                newRefreshTokenResult.opaqueToken(),
                REFRESH_TOKEN_EXPIRATION_DAYS // Duration (60일)
        );

        return TokenResponse.of(
                newAccessToken,
                newRefreshTokenResult,
                role
        );
    }
}