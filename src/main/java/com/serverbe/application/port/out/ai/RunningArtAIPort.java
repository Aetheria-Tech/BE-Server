package com.serverbe.application.port.out.ai;

import com.serverbe.application.port.out.dto.ai.RunningArtGPX;
import com.serverbe.domain.model.art.vo.Proficiency;
import reactor.core.publisher.Mono;

public interface RunningArtAIPort {
    Mono<RunningArtGPX> createRunningArtGPX(Double latitude, Double longitude, String shape, Proficiency proficiency);
}