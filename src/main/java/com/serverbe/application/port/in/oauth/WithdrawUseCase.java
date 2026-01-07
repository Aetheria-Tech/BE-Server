package com.serverbe.application.port.in.oauth;

import reactor.core.publisher.Mono;

public interface WithdrawUseCase {
    /**
     * @param userId 탈퇴할 사용자의 ID(PK)
     * @return 회원 탈퇴가 성공하였는지 여부를 응답합니다.
     * @FR UC-AUTH-03 회원 탈퇴
     */
    Mono<Boolean> withdraw(Long userId);
}