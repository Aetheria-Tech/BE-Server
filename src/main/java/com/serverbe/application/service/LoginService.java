package com.serverbe.application.service;

import com.serverbe.application.port.out.dto.oauth.AccessTokenResponse;
import com.serverbe.application.port.out.dto.oauth.RefreshTokenResponse;
import com.serverbe.application.port.out.dto.oauth.TokenResponse;
import com.serverbe.application.port.out.oauth.OAuthClientPort;
import com.serverbe.application.port.in.oauth.LoginUseCase;
import com.serverbe.application.port.out.security.TokenProvider;
import com.serverbe.application.port.out.redis.TokenPersistencePort;
import com.serverbe.application.port.out.jpa.UserRepositoryPort;
import com.serverbe.domain.model.User;
import com.serverbe.domain.model.vo.OAuthProvider;
import com.serverbe.infrastructure.config.properties.JwtProperties;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@Transactional
public class LoginService implements LoginUseCase {

    private final List<OAuthClientPort> oAuthClients;
    private final UserRepositoryPort userRepositoryPort;
    private final TokenPersistencePort tokenPersistencePort;
    private final TokenProvider tokenProvider;
    private final Duration REFRESH_TOKEN_EXPIRATION_DAYS;

    public LoginService(
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
    public Mono<TokenResponse> login(String code, OAuthProvider provider) {
        // 1. 외부 소셜 서버(카카오/구글) 어댑터 선택 (Strategy Pattern 적용)
        OAuthClientPort client = getClient(provider);

        return client.getUserInfo(code, provider)
                .publishOn(Schedulers.boundedElastic()) // 이후의 블로킹(JPA) 작업을 전용 쓰레드 풀로 넘김
                .map(oauthInfo -> {
                    User user = userRepositoryPort.findByOauthId(oauthInfo.oauthId(), provider)
                            .map(existingUser -> userRepositoryPort.save(existingUser.updateFromOAuth(oauthInfo)))
                            .orElseGet(() -> userRepositoryPort.save(User.createNew(oauthInfo, provider)));

                    // 3. 우리 서비스 전용 JWT 발급 (로그인 로직)
                    AccessTokenResponse accessToken = tokenProvider.generateAccessToken(user.id(), user.role());
                    RefreshTokenResponse refreshTokenResponse = tokenProvider.generateRefreshToken(user.id(), user.role());

                    // 4. Redis에 리프레시 토큰 저장
                    tokenPersistencePort.saveRefreshToken(
                            user.id(),
                            refreshTokenResponse.opaqueToken(),
                            REFRESH_TOKEN_EXPIRATION_DAYS
                    );

                    return TokenResponse.of(accessToken, refreshTokenResponse, user.role());
                });
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