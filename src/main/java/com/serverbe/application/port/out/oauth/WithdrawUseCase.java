package com.serverbe.application.port.out.oauth;

import reactor.core.publisher.Mono;

public interface WithdrawUseCase {
    Mono<Boolean> withdraw(Long userId);
}