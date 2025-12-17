package com.serverbe.application.service;

import com.serverbe.application.port.in.dto.OAuthUserInfo;
import com.serverbe.application.port.in.dto.RefreshTokenIssueResult;
import com.serverbe.application.port.in.dto.TokenResponse;
import com.serverbe.application.port.in.oauth.OAuthClientPort;
import com.serverbe.application.port.in.oauth.SocialLoginUseCase;
import com.serverbe.application.port.in.security.TokenProvider;
import com.serverbe.application.port.out.TokenPersistencePort;
import com.serverbe.application.port.out.UserRepositoryPort;
import com.serverbe.domain.model.User;
import com.serverbe.domain.model.vo.OAuthProvider;
import com.serverbe.infrastructure.config.properties.JwtProperties;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@Transactional
public class SocialLoginService implements SocialLoginUseCase {

    private final List<OAuthClientPort> oAuthClients;
    private final UserRepositoryPort userRepositoryPort;
    private final TokenPersistencePort tokenPersistencePort;
    private final TokenProvider tokenProvider;
    private final Duration REFRESH_TOKEN_EXPIRATION_DAYS;

    public SocialLoginService(
            List<OAuthClientPort> oAuthClients,
            UserRepositoryPort userRepositoryPort,
            TokenPersistencePort tokenPersistencePort,
            TokenProvider tokenProvider,
            JwtProperties jwtProperties
    ) {
        this.oAuthClients = oAuthClients;
        this.userRepositoryPort = userRepositoryPort;
        this.tokenPersistencePort = tokenPersistencePort;
        this.tokenProvider = tokenProvider;
        REFRESH_TOKEN_EXPIRATION_DAYS = jwtProperties.refreshToken().expirationDays();
    }

    @Override
    public TokenResponse login(String code, OAuthProvider provider) {
        // 1. 외부 소셜 서버(카카오/구글) 어댑터 선택 (Strategy Pattern 적용)
        OAuthClientPort client = getClient(provider);

        OAuthUserInfo oauthInfo = client.getUserInfo(code, provider);

        // 2. DB에서 기존 유저인지 확인 (Upsert 로직)
        // Record의 불변성을 활용하여 새로운 객체를 저장
        User user = userRepositoryPort.findByOauthId(oauthInfo.oauthId(), provider)
                .map(existingUser -> userRepositoryPort.save(existingUser.updateFromOAuth(oauthInfo)))
                .orElseGet(() -> userRepositoryPort.save(User.createNew(oauthInfo, provider)));

        // 3. 우리 서비스 전용 JWT 발급 (로그인 로직)
        String accessToken = tokenProvider.generateAccessToken(user.id(), user.role());
        RefreshTokenIssueResult refreshTokenIssueResult = tokenProvider.generateRefreshToken(user.id(), user.role());

        // 4. Redis에 리프레시 토큰 저장
        tokenPersistencePort.saveRefreshToken(
                user.id(),
                refreshTokenIssueResult.opaqueToken(),
                REFRESH_TOKEN_EXPIRATION_DAYS
        );

        return TokenResponse.of(accessToken, refreshTokenIssueResult, user.role());
    }

    /**
     * Provider(KAKAO, GOOGLE)에 맞는 구현체를 List에서 찾아 반환합니다.
     */
    private OAuthClientPort getClient(OAuthProvider provider) {
        return oAuthClients.stream()
                .filter(client -> client.supports(provider))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorMessage.INTERNAL_SERVER_ERROR, "지원하지 않는 소셜 로그인입니다."));
    }

    @Override
    public String getSocialLoginUrl(OAuthProvider provider) {
        return getClient(provider).getLoginUrl();
    }
}