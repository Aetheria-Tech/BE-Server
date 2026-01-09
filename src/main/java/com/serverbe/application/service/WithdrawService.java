package com.serverbe.application.service;

import com.serverbe.application.port.out.jpa.RunningArtRepositoryPort;
import com.serverbe.application.port.out.oauth.OAuthClientPort;
import com.serverbe.application.port.in.oauth.WithdrawUseCase;
import com.serverbe.application.port.out.token.TokenPersistencePort;
import com.serverbe.application.port.out.jpa.UserRepositoryPort;
import com.serverbe.domain.model.user.vo.OAuthProvider;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * @author Duskafka
 * @responsibility 회원탈퇴를 수행하는 책임
 * @see WithdrawUseCase
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawService implements WithdrawUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final RunningArtRepositoryPort runningArtRepositoryPort;
    private final List<OAuthClientPort> oAuthClients;
    private final TokenPersistencePort tokenPersistencePort;

    /**
     * @param userId 탈퇴할 사용자의 식별자
     * @return 탈퇴 완료 여부 (성공 시 true)
     * @requirement UC-AUTH-03: 회원 탈퇴
     * @responsibility 소셜 서비스 연동을 해제하고, 성공 시 시스템 내 사용자의 모든 활동 및 인증 데이터를 삭제합니다.
     * @implSpec 1. {@link Schedulers#boundedElastic()}을 사용하여 블로킹 작업(DB)을 비동기적으로 처리합니다.<br>
     * 2. 외부 OAuth 플랫폼의 unlink를 우선 수행하며, 실패 시 내부 삭제를 중단하여 데이터 정합성을 유지합니다.<br>
     * 3. {@link Transactional}을 통해 내부 데이터(DB/Redis) 삭제의 원자성을 보장합니다.
     * @implNote 외부 서버와 통신하므로 리액티브 스트림({@link Mono})으로 응답을 반환합니다.
     */
    @Override
    @Transactional
    public Mono<Boolean> withdraw(Long userId) {
        return Mono.fromCallable(() -> userRepositoryPort.findById(userId)
                        .orElseThrow(() -> new BusinessException(ErrorMessage.NOT_FOUND_USER))
                )
                .subscribeOn(Schedulers.boundedElastic()) // 블로킹 DB 조회를 별도 스레드에서 실행
                .flatMap(user -> {
                    OAuthClientPort client = getClient(user.provider());
                    // 소셜 서비스 연동 해제 요청 (unlink)
                    return client.unlink(user.provider(), user.oauthId(), user.oauthRefreshToken())
                            .publishOn(Schedulers.boundedElastic()) // 후속 블로킹 작업(DB, Redis 삭제)도 별도 스레드에서 실행
                            .map(isUnlink -> {
                                if (isUnlink) {
                                    runningArtRepositoryPort.deleteByUserId(userId);
                                    // 우리 DB에서 사용자 삭제 (Hard Delete)
                                    userRepositoryPort.deleteById(user.id());
                                    // Redis에서 리프레쉬 토큰 삭제
                                    tokenPersistencePort.deleteRefreshToken(user.id());
                                    return true;
                                }
                                return false;
                            });
                });
    }

    /**
     * @param provider 소셜 제공자 구분값
     * @return 일치하는 {@link OAuthClientPort} 구현체
     * @responsibility 제공된 {@link OAuthProvider}를 지원하는 클라이언트 어댑터를 검색합니다.
     * @implNote 전략 패턴을 활용하여 런타임에 적절한 OAuth 구현체를 선택합니다.
     */
    private OAuthClientPort getClient(OAuthProvider provider) {
        return oAuthClients.stream()
                .filter(client -> client.supports(provider))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorMessage.INTERNAL_SERVER_ERROR, "지원하지 않는 소셜 로그인입니다."));
    }
}