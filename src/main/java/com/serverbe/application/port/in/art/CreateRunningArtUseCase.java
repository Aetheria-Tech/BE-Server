package com.serverbe.application.port.in.art;

import com.serverbe.application.port.in.dto.art.RunningArtResult;
import com.serverbe.domain.model.art.vo.Proficiency;
import reactor.core.publisher.Mono;

public interface CreateRunningArtUseCase {
    Mono<RunningArtResult> createRunningArt(Long userId, String startPosition, String shape, Proficiency proficiency);
}