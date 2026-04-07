package com.serverbe.adapter.out.persistence.art;

import com.serverbe.application.port.out.ai.RunningArtRedisPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class RunningArtRedisAdapter implements RunningArtRedisPort {
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private static final String GEO_KEY = "running_art:locations";

    @Override
    public Mono<Long> saveLocation(Long id, Double lat, Double lon) {
        return reactiveRedisTemplate.opsForGeo()
                .add(GEO_KEY, new Point(lon, lat), id.toString());
    }

    /**
     * @param userLat 중심점 위도
     * @param userLon 중심점 경도
     * @param radiusKm 검색 반경 크기 (km)
     * @return 반경 내 검색된 런닝 아트의 ID를 방출하는 {@link Flux<Long>}
     * @responsibility Redis GEORADIUS 명령어를 사용하여 특정 반경 내의 데이터를 조회합니다.
     */
    @Override
    public Flux<Long> findNearbyIds(Double userLat, Double userLon, Double radiusKm) {

        // 1. 중심점(Point)과 거리(Distance)를 하나로 묶어 원(Circle) 객체 생성
        Circle circle = new Circle(
                new Point(userLon, userLat),
                new Distance(radiusKm, Metrics.KILOMETERS)
        );

        // 2. search 대신 radius 메서드 사용!
        return reactiveRedisTemplate.opsForGeo()
                .radius(
                        GEO_KEY,
                        circle,
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().sortAscending() // 정렬도 Radius 전용 Args로 변경
                )
                .map(result -> Long.parseLong(result.getContent().getName()));
    }

    @Override
    public Mono<Long> removeLocation(Long id) {
        return reactiveRedisTemplate.opsForGeo()
                .remove(GEO_KEY, id.toString());
    }

    @Override
    public Mono<Boolean> clearAllLocations() {
        return reactiveRedisTemplate.delete(GEO_KEY)
                .map(count -> count > 0)
                .defaultIfEmpty(false);
    }
}