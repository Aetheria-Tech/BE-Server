package com.serverbe.application.port.in.art;

import com.serverbe.adapter.in.web.dto.art.RunningArtResponse;
import com.serverbe.application.port.in.dto.art.RunningArtResult;
import reactor.core.publisher.Flux;

public interface GetNearbyRunningArtUseCase {
    Flux<RunningArtResult> getNearbyArts(Double lat, Double lon, Double radius);
}