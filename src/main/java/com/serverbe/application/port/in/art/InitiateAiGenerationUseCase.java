package com.serverbe.application.port.in.art;

import com.serverbe.domain.model.art.vo.Proficiency;
import reactor.core.publisher.Mono;

public interface InitiateAiGenerationUseCase {
    /**
     * AI 런닝 아트 생성을 비동기로 요청하고 Task ID를 반환합니다.
     */
    Mono<String> initiateGeneration(Long userId, String startPosition, String shape, Proficiency proficiency);
}