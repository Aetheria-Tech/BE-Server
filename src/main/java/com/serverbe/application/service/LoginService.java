package com.serverbe.application.service;

import com.serverbe.application.port.out.dto.oauth.AccessTokenResult;
import com.serverbe.application.port.out.dto.oauth.RefreshTokenResult;
import com.serverbe.application.port.out.dto.oauth.TokenResult;
import com.serverbe.application.port.out.oauth.OAuthClientPort;
import com.serverbe.application.port.in.oauth.LoginUseCase;
import com.serverbe.application.port.out.security.TokenProvider;
import com.serverbe.application.port.out.token.TokenPersistencePort;
import com.serverbe.application.port.out.jpa.UserRepositoryPort;
import com.serverbe.domain.model.user.User;
import com.serverbe.domain.model.user.vo.OAuthProvider;
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

/**
 * @author Duskafka
 * @responsiblity 로그인 및 회원가입을 수행해주는 책임
 * @see LoginUseCase
 */
@Slf4j
@Service
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

    /**
     * @param code     OAuth 서버에서 응답해준 로그인 코드로 이를 사용해서 사용자 정보를 OAuth 서버에서 받아옵니다.
     * @param provider KAKAO 또는 GOOGLE 같은 로그인 방식을 식별할 수 있는 Enum
     * @return 액세스 토큰 정보, 리프레시 토큰 정보, 사용자 권한 정보를 담은 DTO
     * @FR UC-AUTH-01 로그인 및 회원가입
     * @responsiblity 로그인을 수행하는 책임을 가진 메소드
     * @implSpec 비동기 스레드에서 작업을 진행하다가 JPA로 데이터베이스를 조회할 때 전용 스레드 풀로 작업을 넘긴다.
     * 따라서 이 메소드 스레드 흐름은 다음과 같다 {@code 비동기 -> 동기 -> 비동기}
     * @see LoginUseCase#login(String, OAuthProvider)
     */
    @Override
    @Transactional
    public Mono<TokenResult> login(String code, OAuthProvider provider) {
        // 1. 외부 소셜 서버(카카오/구글) 어댑터 선택 (Strategy Pattern 적용)
        OAuthClientPort client = getClient(provider);

        // 2. OAuth 서버에서 사용자 정보를 가져옴(이름, 이메일)
        return client.getUserInfo(code, provider)
                .publishOn(Schedulers.boundedElastic()) // 이후의 블로킹(JPA) 작업을 전용 쓰레드 풀로 넘김
                .map(oauthInfo -> {
                    User user = userRepositoryPort.findByOauthId(oauthInfo.oauthId(), provider)
                            .map(existingUser -> userRepositoryPort.save(existingUser.updateFromOAuth(oauthInfo)))
                            .orElseGet(() -> userRepositoryPort.save(User.createNew(oauthInfo, provider)));

                    // 3. 우리 서비스 전용 JWT 발급 (로그인 로직)
                    AccessTokenResult accessTokenResult = tokenProvider.generateAccessToken(user.id(), user.role());
                    RefreshTokenResult refreshTokenResult = tokenProvider.generateRefreshToken(user.id(), user.role());

                    // 4. Redis에 리프레시 토큰 저장
                    tokenPersistencePort.saveRefreshToken(
                            user.id(),
                            refreshTokenResult.opaqueToken(),
                            REFRESH_TOKEN_EXPIRATION_DAYS
                    );

                    // 5. TokenResult 객체에 액세스 토큰, 리프레시 토큰, 사용자 권한을 넘겨서 응답.
                    return TokenResult.of(accessTokenResult, refreshTokenResult, user.role());
                });
    }

    /**
     * @param provider KAKAO 또는 GOOGLE 같은 로그인 방식을 식별할 수 있는 Enum
     * @return 로그인을 진행할 수 있는 URL을 응답한다.
     * @responsiblity 각 로그인 방식(OAuth 서버)에 따라 다른 로그인 URL을 응답하는 책임을 가진 메소드
     * @see LoginUseCase#getSocialLoginUrl(OAuthProvider)
     */
    @Override
    public String getSocialLoginUrl(OAuthProvider provider) {
        return getClient(provider).getLoginUrl();
    }

    /**
     * @param provider KAKAO 또는 GOOGLE 같은 로그인 방식을 식별할 수 있는 Enum
     * @responsiblity 요청에 알맞은 {@code OAuthClientPort}를 찾도록 도와주는 책임을 가진다.
     * @implNote Provider(KAKAO, GOOGLE)에 맞는 구현체를 List에서 찾아 반환합니다.
     * @see com.serverbe.adapter.out.external.google.GoogleOAuthAdapter
     * @see com.serverbe.adapter.out.external.kakao.KakaoOAuthAdapter
     */
    private OAuthClientPort getClient(OAuthProvider provider) {
        return oAuthClients.stream()
                .filter(client -> client.supports(provider))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorMessage.INTERNAL_SERVER_ERROR, "지원하지 않는 소셜 로그인입니다."));
    }
}