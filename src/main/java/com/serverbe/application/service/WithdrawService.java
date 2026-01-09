package com.serverbe.application.service;

import com.serverbe.application.port.out.oauth.OAuthClientPort;
import com.serverbe.application.port.in.oauth.WithdrawUseCase;
import com.serverbe.application.port.out.jpa.UserRepositoryPort;
import com.serverbe.application.service.helper.UserDataCleanupManager;
import com.serverbe.domain.model.user.vo.OAuthProvider;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * @author Duskafka
 * @responsibility 사용자의 회원 탈퇴 요청을 처리하며, 외부 소셜 연동 해제와 내부 데이터 파기를 조율합니다.
 * @implSpec {@link WithdrawUseCase}의 구현체이며, 외부 서비스와의 통신 결과에 따라 내부 데이터 삭제 여부를 결정합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawService implements WithdrawUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final List<OAuthClientPort> oAuthClients;
    private final UserDataCleanupManager userDataCleanupManager;

    /**
     * @param userId 탈퇴할 사용자의 식별자
     * @return 탈퇴 완료 여부 (성공 시 {@code true})
     * @responsibility 소셜 연동 해제(Unlink)를 수행하고, 성공 시 시스템 내 사용자의 모든 데이터를 삭제(Hard Delete)합니다.
     * @requirement <b>UC-AUTH-03: 회원 탈퇴</b>
     * @implSpec 1. <b>데이터 정합성</b>: 외부 플랫폼(OAuth) 연동 해제가 성공한 경우에만 내부 데이터 삭제를 진행합니다.<br>
     * 2. <b>스레드 관리</b>: 블로킹 I/O 작업(JPA)을 수행하기 전 {@link Schedulers#boundedElastic()}을 통해 실행 컨텍스트를 전환합니다.<br>
     * 3. <b>트랜잭션 보장</b>: 자기 호출 문제를 방지하기 위해 {@link UserDataCleanupManager}를 통해 외부 호출 방식으로 데이터 파기를 수행합니다.
     */
    @Override
    public Mono<Boolean> withdraw(Long userId) {
        return Mono.fromCallable(() -> userRepositoryPort.findById(userId)
                        .orElseThrow(() -> new BusinessException(ErrorMessage.NOT_FOUND_USER)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(user -> {
                    OAuthClientPort client = getClient(user.provider());

                    // 1. 외부 소셜 서비스 연동 해제 (Unlink)
                    return client.unlink(user.provider(), user.oauthId(), user.oauthRefreshToken())
                            .publishOn(Schedulers.boundedElastic()) // 블로킹 삭제 작업을 위한 스케줄러 전환
                            .map(isUnlink -> {
                                if (Boolean.TRUE.equals(isUnlink)) {
                                    // 2. 내부 데이터 파기 (트랜잭션 보장)
                                    userDataCleanupManager.deleteAllUserData(userId);

                                    log.info("[WITHDRAW] 사용자 데이터 파기 완료. 사용자 ID: {}, 소셜 제공자: {}", userId, user.provider());
                                    return true;
                                }

                                log.warn("[WITHDRAW FAIL] 소셜 연동 해제 거부 또는 실패. 사용자 ID: {}", userId);
                                return false;
                            });
                });
    }

    /**
     * @responsibility 요청된 {@link OAuthProvider}에 대응하는 {@link OAuthClientPort} 구현체를 전략적으로 선택합니다.
     */
    private OAuthClientPort getClient(OAuthProvider provider) {
        return oAuthClients.stream()
                .filter(client -> client.supports(provider))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("[SECURITY/CONFIG ERROR] 지원하지 않는 소셜 로그인 방식 요청: {}", provider);
                    return new BusinessException(ErrorMessage.INTERNAL_SERVER_ERROR);
                });
    }
}