package com.serverbe.adapter.out.persistence.art;

import com.serverbe.application.port.out.ai.RunningArtRedisPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class RunningArtRedisAdapter implements RunningArtRedisPort {
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private static final String GEO_KEY = "running_art:locations";

    // 1. 위치 저장 (GEOADD)
    @Override
    public Mono<Long> saveLocation(Long id, Double lat, Double lon) {
        return reactiveRedisTemplate.opsForGeo()
                .add(GEO_KEY, new Point(lon, lat), id.toString());
    }

    // 2. 반경 검색 (GEOSEARCH)
    @Override
    public Flux<Long> findNearbyIds(Double userLat, Double userLon, Double radiusKm) {
        return reactiveRedisTemplate.opsForGeo()
                .search(
                        GEO_KEY,
                        GeoReference.fromCoordinate(userLon, userLat),
                        new Distance(radiusKm, Metrics.KILOMETERS),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().sortAscending()
                )
                .map(result -> Long.parseLong(result.getContent().getName()));
    }
}