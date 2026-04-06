package com.serverbe.application.port.out.ai;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RunningArtRedisPort {
    Mono<Long> saveLocation(Long id, Double lat, Double lon);
    Flux<Long> findNearbyIds(Double userLat, Double userLon, Double radiusKm);
}
