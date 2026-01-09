package com.serverbe.application.service;

import com.serverbe.application.port.out.dto.oauth.OAuthUserInfoResult;
import com.serverbe.application.port.out.dto.oauth.TokenResult;
import com.serverbe.application.port.out.oauth.OAuthClientPort;
import com.serverbe.application.port.in.oauth.LoginUseCase;
import com.serverbe.application.port.out.security.TokenProvider;
import com.serverbe.application.port.out.token.TokenPersistencePort;
import com.serverbe.application.port.out.jpa.UserRepositoryPort;
import com.serverbe.domain.model.user.User;
import com.serverbe.domain.model.user.vo.OAuthProvider;
import com.serverbe.domain.model.user.vo.Role;
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
    private final Duration refreshTokenExpirationDays;

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
        refreshTokenExpirationDays = jwtProperties.refreshToken().expirationDays();
    }

    /**
     * @param code     소셜 플랫폼에서 발행한 인가 코드
     * @param provider 인증을 수행할 소셜 제공자 {@link OAuthProvider}
     * @return 액세스/리프레시 토큰 및 권한 정보를 포함한 {@link Mono}
     * @responsibility 소셜 인가 코드를 사용하여 사용자를 인증하고, 회원 정보 동기화 후 토큰 세트를 발급합니다.
     * @implNote <b>Thread Switching</b>: 외부 API 응답 수신 후, JPA의 Blocking I/O 작업을 수행하기 위해
     * {@link Schedulers#boundedElastic()}으로 실행 컨텍스트를 전환합니다. 이는 Netty 이벤트 루프의 가용성을 보장하기 위함입니다.
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
                    log.debug("[OAUTH   ] 소셜 사용자 정보 수신 성공: OAuthID={}, Provider={}", oauthInfo.oauthId(), provider);
                    User user = syncUserByOAuth(oauthInfo);

                    // 3. 우리 서비스 전용 JWT 발급
                    TokenResult newTokens = generateTokens(user.id(), user.role());

                    // 4. Redis에 리프레시 토큰 저장
                    saveRefreshTokenInRedis(user.id(), newTokens.refreshTokenResult().opaqueToken());

                    return newTokens;
                });
    }

    /**
     * @param oauthInfo 외부에서 제공받은 유저 프로필 정보
     * @return 동기화된 {@link User} 엔티티
     * @responsibility 제공받은 소셜 프로필 정보를 시스템 유저 정보와 동기화합니다. (Upsert)
     * @implNote 산탄총 수술(Shotgun Surgery)을 방지하기 위해 생성 및 수정 로직을 하나로 캡슐화하였으며,
     * 기존 회원은 정보를 갱신하고 신규 회원은 생성합니다.
     */
    private User syncUserByOAuth(OAuthUserInfoResult oauthInfo) {
        return userRepositoryPort.findByOauthId(oauthInfo.oauthId(), oauthInfo.provider())
                .map(existingUser -> {
                    log.info("[LOGIN] 기존 회원 접속: ID={}, Provider={}", existingUser.id(), oauthInfo.provider());
                    return userRepositoryPort.save(existingUser.updateFromOAuth(oauthInfo));
                })
                .orElseGet(() -> {
                    User newUser = userRepositoryPort.save(User.createNew(oauthInfo, oauthInfo.provider()));
                    log.info("[REGISTER] 신규 회원 가입 완료: ID={}, Provider={}", newUser.id(), oauthInfo.provider());
                    return newUser;
                });
    }

    /**
     * @param userId 토큰에 포함될 사용자 고유 식별자
     * @param role   사용자에게 부여된 시스템 권한 {@link Role}
     * @return 액세스 토큰과 리프레시 토큰이 포함된 {@link TokenResult}
     * @responsibility 유저 식별자와 권한 정보를 기반으로 서비스 표준 보안 토큰(Access/Refresh) 쌍을 생성합니다.
     * @implNote 1. {@link TokenProvider}를 통해 각 토큰의 클레임(Claims)을 구성하고 서명합니다.<br>
     * 2. 생성된 토큰들은 {@link TokenResult} DTO에 담겨 클라이언트로 반환될 준비를 마칩니다.
     */
    private TokenResult generateTokens(Long userId, Role role) {
        return TokenResult.of(
                tokenProvider.generateAccessToken(userId, role),
                tokenProvider.generateRefreshToken(userId, role),
                role
        );
    }

    /**
     * @param userId       토큰을 소유한 사용자의 식별자
     * @param refreshToken Redis에 저장할 불투명(Opaque) 리프레시 토큰 문자열
     * @responsibility 발급된 리프레시 토큰을 Redis 저장소에 영속화하여 향후 세션 검증 및 재발급(RTR)에 활용합니다.
     * @implSpec 1. <b>TTL(Time-To-Live) 설정</b>: {@code refreshTokenExpirationDays} 정책에 따라 자동으로 만료되도록 설정합니다.<br>
     * 2. <b>세션 관리</b>: 유저 ID를 키로 활용하여 기존의 유효하지 않은 세션을 관리하거나 대체합니다.
     */
    private void saveRefreshTokenInRedis(Long userId, String refreshToken) {
        tokenPersistencePort.saveRefreshToken(
                userId,
                refreshToken,
                refreshTokenExpirationDays
        );
    }

    /**
     * @param provider 로그인 페이지를 요청할 소셜 제공자 {@link OAuthProvider}
     * @return 소셜 로그인 리다이렉트 URL {@link String}
     * @throws BusinessException <b>INTERNAL_SERVER_ERROR</b>: 지원하지 않는 소셜 로그인 방식이 요청되었을 경우 발생합니다.
     * @responsibility 요청된 소셜 제공자에 최적화된 인증 페이지 URL을 반환합니다.
     * @see LoginUseCase#getSocialLoginUrl(OAuthProvider)
     */
    @Override
    public String getSocialLoginUrl(OAuthProvider provider) {
        return getClient(provider).getLoginUrl();
    }

    /**
     * @param provider 지원 여부를 확인할 소셜 제공자
     * @return 일치하는 {@link OAuthClientPort} 구현체
     * @throws BusinessException <b>INTERNAL_SERVER_ERROR</b>: <b>provider</b>에 해당하는 소셜 로그인 구현체를 찾을 수 없을 경우 발생합니다.
     * @responsibility 요청에 알맞은 {@link OAuthClientPort}를 찾도록 도와주는 책임을 가진다.
     * @implNote Provider(KAKAO, GOOGLE)에 맞는 구현체를 List에서 찾아 반환합니다.
     * @see com.serverbe.adapter.out.external.google.GoogleOAuthAdapter
     * @see com.serverbe.adapter.out.external.kakao.KakaoOAuthAdapter
     */
    private OAuthClientPort getClient(OAuthProvider provider) {
        return oAuthClients.stream()
                .filter(client -> client.supports(provider))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("[SECURITY ALERT] 지원하지 않는 소셜 로그인 요청입니다. 요청된 Provider: {}", provider);
                    return new BusinessException(ErrorMessage.INTERNAL_SERVER_ERROR, "지원하지 않는 소셜 로그인입니다.");
                });
    }
}