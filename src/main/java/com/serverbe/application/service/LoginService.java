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
 * @responsibility 외부 소셜 서비스 인증 결과를 기반으로 시스템 회원 가입 및 로그인 프로세스를 처리하고 보안 토큰을 발행합니다.
 * @implSpec {@link LoginUseCase}의 구현체이며, 다중 소셜 플랫폼 지원을 위해 전략 패턴(Strategy Pattern)을 활용합니다.
 */
@Slf4j
@Service
public class LoginService implements LoginUseCase {

    private final List<OAuthClientPort> oAuthClients;
    private final UserRepositoryPort userRepositoryPort;
    private final TokenPersistencePort tokenPersistencePort;
    private final TokenProvider tokenProvider;
    private final Duration REFRESH_TOKEN_EXPIRATION_DAYS;

    /**
     * @implSpec {@link JwtProperties}로부터 토큰 만료 정책을 주입받아 리프레시 토큰의 유효 기간을 설정합니다.
     */
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
     * @requirement UC-AUTH-01: 로그인 및 회원가입
     * @responsibility 소셜 인가 코드를 사용하여 사용자를 인증하고, 신규 회원일 경우 가입 처리를 진행한 뒤 서비스 전용 토큰 세트를 발급합니다.
     * @implSpec
     * 1. <b>스레드 전환</b>: 외부 API 응답 수신 후, 블로킹 I/O(JPA) 처리를 위해 {@link Schedulers#boundedElastic()}으로 스케줄러를 전환합니다.<br>
     * 2. <b>계정 식별 정책</b>: 이메일 중복 문제를 방지하기 위해 'OAuth Provider + OAuth ID' 조합을 유니크 키로 사용하여 사용자를 식별합니다.<br>
     * 3. <b>Upsert 로직</b>: 기존 회원은 최신 소셜 프로필로 업데이트(Save)하고, 미가입자는 신규 엔티티를 생성하여 영속화합니다.<br>
     * 4. <b>세션 관리</b>: 발급된 리프레시 토큰은 Redis에 저장하여 향후 RTR(Reissue) 처리에 활용합니다.
     * @implNote 동일 이메일이라도 소셜 제공자가 다르면 별개 계정으로 취급하여 계정 탈취(Account Takeover) 위험을 방지합니다.
     * @param code 소셜 플랫폼에서 발행한 인가 코드
     * @param provider 인증을 수행할 소셜 제공자 {@link OAuthProvider}
     * @return 액세스/리프레시 토큰 및 권한 정보를 포함한 {@link Mono<TokenResult>}
     * @throws BusinessException <b>INTERNAL_SERVER_ERROR</b>: 지원하지 않는 소셜 로그인 방식이 요청되었을 경우 발생합니다.
     * @see LoginUseCase#login(String, OAuthProvider)
     * @see <a href="https://tools.ietf.org/html/rfc6749">The OAuth 2.0 Authorization Framework</a>
     */
    @Override
    @Transactional
    public Mono<TokenResult> login(String code, OAuthProvider provider) {
        // 1. 외부 소셜 서버(카카오/구글) 어댑터 선택
        OAuthClientPort client = getClient(provider);

        // 2. OAuth 서버에서 사용자 정보를 가져옴
        return client.getUserInfo(code, provider)
                .publishOn(Schedulers.boundedElastic())
                .map(oauthInfo -> {
                    User user = userRepositoryPort.findByOauthId(oauthInfo.oauthId(), provider)
                            .map(existingUser -> userRepositoryPort.save(existingUser.updateFromOAuth(oauthInfo)))
                            .orElseGet(() -> userRepositoryPort.save(User.createNew(oauthInfo, provider)));

                    // 3. 우리 서비스 전용 JWT 발급
                    AccessTokenResult accessTokenResult = tokenProvider.generateAccessToken(user.id(), user.role());
                    RefreshTokenResult refreshTokenResult = tokenProvider.generateRefreshToken(user.id(), user.role());

                    // 4. Redis에 리프레시 토큰 저장
                    tokenPersistencePort.saveRefreshToken(
                            user.id(),
                            refreshTokenResult.opaqueToken(),
                            REFRESH_TOKEN_EXPIRATION_DAYS
                    );

                    return TokenResult.of(accessTokenResult, refreshTokenResult, user.role());
                });
    }

    /**
     * @responsibility 요청된 소셜 제공자에 최적화된 인증 페이지 URL을 반환합니다.
     * @param provider 로그인 페이지를 요청할 소셜 제공자 {@link OAuthProvider}
     * @return 소셜 로그인 리다이렉트 URL {@link String}
     * @throws BusinessException <b>INTERNAL_SERVER_ERROR</b>: 지원하지 않는 소셜 로그인 방식이 요청되었을 경우 발생합니다.
     * @see LoginUseCase#getSocialLoginUrl(OAuthProvider)
     */
    @Override
    public String getSocialLoginUrl(OAuthProvider provider) {
        return getClient(provider).getLoginUrl();
    }

    /**
     * @responsibility 요청에 알맞은 {@link OAuthClientPort}를 찾도록 도와주는 책임을 가진다.
     * @implNote Provider(KAKAO, GOOGLE)에 맞는 구현체를 List에서 찾아 반환합니다.
     * @param provider 지원 여부를 확인할 소셜 제공자
     * @return 일치하는 {@link OAuthClientPort} 구현체
     * @throws BusinessException <b>INTERNAL_SERVER_ERROR</b>: <b>provider</b>에 해당하는 소셜 로그인 구현체를 찾을 수 없을 경우 발생합니다.
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