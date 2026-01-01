package com.serverbe.application.service;

import com.serverbe.application.port.out.jpa.RunningArtRepositoryPort;
import com.serverbe.application.port.out.oauth.OAuthClientPort;
import com.serverbe.application.port.in.oauth.WithdrawUseCase;
import com.serverbe.application.port.out.token.TokenPersistencePort;
import com.serverbe.application.port.out.jpa.UserRepositoryPort;
import com.serverbe.domain.model.user.vo.OAuthProvider;
import com.serverbe.infrastructure.common.logging.Trace;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawService implements WithdrawUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final RunningArtRepositoryPort runningArtRepositoryPort;
    private final List<OAuthClientPort> oAuthClients;
    private final TokenPersistencePort tokenPersistencePort;

    @Override
    @Transactional
    public Mono<Boolean> withdraw(Long userId) {
        return Mono.fromCallable(() -> userRepositoryPort.findById(userId)
                        .orElseThrow(() -> new BusinessException(ErrorMessage.NOT_FOUND_USER)))
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

    // Provider에 맞는 구현체를 찾는 헬퍼 메서드
    private OAuthClientPort getClient(OAuthProvider provider) {
        return oAuthClients.stream()
                .filter(client -> client.supports(provider))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorMessage.INTERNAL_SERVER_ERROR, "지원하지 않는 소셜 로그인입니다."));
    }
}