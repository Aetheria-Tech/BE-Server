package com.serverbe.application.port.in.art;

import com.serverbe.domain.model.art.vo.Proficiency;

public interface InitiateAiGenerationUseCase {
    /**
     * AI 런닝 아트 생성을 비동기로 요청하고 Task ID를 반환합니다.
     */
    String initiateGeneration(Long userId, String startPosition, String shape, Proficiency proficiency);
}