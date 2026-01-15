package com.serverbe.application.service;

import com.serverbe.application.port.in.oauth.LoginUseCase;
import com.serverbe.application.port.out.dto.oauth.TokenResult;
import com.serverbe.application.port.out.oauth.OAuthClientPort;
import com.serverbe.application.port.out.security.TokenProvider;
import com.serverbe.application.service.helper.AuthSessionManager;
import com.serverbe.application.service.helper.UserDataSyncManager;
import com.serverbe.domain.exception.auth.AuthErrorCode;
import com.serverbe.domain.exception.auth.AuthException;
import com.serverbe.domain.model.user.User;
import com.serverbe.domain.model.user.vo.OAuthProvider;
import com.serverbe.domain.model.user.vo.Role;
import com.serverbe.domain.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * @author Duskafka
 * @responsibility 외부 소셜 인증 결과를 시스템 내부 사용자 정보와 동기화하고, 보안 세션을 생성하는 총괄 지휘자 역할을 수행합니다.
 * @implSpec 1. <b>전략 패턴</b>: {@link OAuthClientPort}를 통해 다중 소셜 플랫폼을 지원합니다.<br>
 * 2. <b>책임 분리</b>: DB 트랜잭션은 {@link UserDataSyncManager}에게, Redis 세션 관리는 {@link AuthSessionManager}에게 위임합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService implements LoginUseCase {

    private final List<OAuthClientPort> oAuthClients;
    private final UserDataSyncManager userDataSyncManager;
    private final AuthSessionManager authSessionManager;
    private final TokenProvider tokenProvider;

    /**
     * @param code     소셜 플랫폼 인가 코드
     * @param deviceId 사용자의 기기 식별자
     * @param provider 인증 제공자
     * @return 발급된 토큰 세트를 포함한 {@link Mono}
     * @responsibility 소셜 인증 코드를 검증하여 사용자 정보를 획득하고, 가입/로그인 처리 후 최종 보안 토큰을 반환합니다.
     * @requirement <b>UC-AUTH-01: 소셜 로그인 및 회원가입</b>
     * @implNote 1. 외부 API 통신 후 {@link Schedulers#boundedElastic()}으로 전환하여 블로킹 I/O 작업을 안전하게 처리합니다.<br>
     * 2. 서비스 레이어에서의 {@code @Transactional}은 헬퍼 컴포넌트 내부로 전파되어 원자성을 보장받습니다.
     */
    @Override
    public Mono<TokenResult> login(String code, OAuthProvider provider, String deviceId) {
        OAuthClientPort client = getClient(provider);

        return client.getUserInfo(code, provider)
                .publishOn(Schedulers.boundedElastic())
                .map(oauthInfo -> {
                    log.debug("[OAUTH] 소셜 사용자 정보 수신 성공: OAuthID={}, Provider={}", oauthInfo.oauthId(), provider);

                    // 1. 유저 정보 동기화 (JPA 트랜잭션 보장)
                    User user = userDataSyncManager.syncUserByOAuth(oauthInfo);

                    // 2. 서비스 전용 토큰 생성 (JWT)
                    TokenResult newTokens = generateTokens(user.id(), user.role());

                    // 3. 보안 세션 저장 (Redis TTL 관리)
                    authSessionManager.saveSession(user.id(), deviceId, newTokens.refreshTokenResult().opaqueToken());

                    return newTokens;
                });
    }

    /**
     * @param userId 사용자 식별자
     * @param role   사용자 권한
     * @return 생성된 {@link TokenResult}
     * @responsibility 유저의 식별자와 권한을 기반으로 신규 Access/Refresh 토큰 쌍을 생성합니다.
     */
    private TokenResult generateTokens(Long userId, Role role) {
        return TokenResult.of(
                tokenProvider.generateAccessToken(userId, role),
                tokenProvider.generateRefreshToken(userId, role),
                role
        );
    }

    /**
     * @param provider 소셜 제공자
     * @return 리다이렉트 URL 문자열
     * @responsibility 소셜 플랫폼별 최적화된 인증 페이지 URL을 획득합니다.
     */
    @Override
    public String getSocialLoginUrl(OAuthProvider provider) {
        return getClient(provider).getLoginUrl();
    }

    /**
     * @throws BusinessException 지원하지 않는 플랫폼 요청 시 발생
     * @responsibility 제공된 {@link OAuthProvider}를 지원하는 클라이언트 어댑터를 검색합니다.
     */
    private OAuthClientPort getClient(OAuthProvider provider) {
        return oAuthClients.stream()
                .filter(client -> client.supports(provider))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("[SECURITY ALERT] 미지원 인증 요청: Provider={}", provider);
                    return new AuthException(AuthErrorCode.UNSUPPORTED_SOCIAL_LOGIN);
                });
    }
}