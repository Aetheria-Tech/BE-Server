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
     * @param userId 탈퇴할 사용자의 ID(PK)
     * @return 회원 탈퇴가 성공하였는지 여부를 응답합니다.
     * @responsibility 사용자의 회원 탈퇴 요청을 수행한다.
     * @requirement UC-AUTH-03: 회원 탈퇴
     * @implNote 외부 OAuth 서버에 회원 탈퇴를 요청합니다.
     * @implSpec 이 메소드는 회원탈퇴가 외부 OAuth 서버에서 진행된 후 데이터베이스에서 정보 삭제를 위해 별도 스레드에서 작업을 수행합니다.
     * @see WithdrawUseCase#withdraw(Long) 구현하는 유즈케이스
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
     * @param provider 구현체를 찾는데 필요한 Enum
     * @return {@code Provider}에 맞는 {@code OAuthClientPort} 구현체
     * @implNote Provider에 맞는 구현체를 찾는 헬퍼 메서드
     */
    private OAuthClientPort getClient(OAuthProvider provider) {
        return oAuthClients.stream()
                .filter(client -> client.supports(provider))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorMessage.INTERNAL_SERVER_ERROR, "지원하지 않는 소셜 로그인입니다."));
    }
}