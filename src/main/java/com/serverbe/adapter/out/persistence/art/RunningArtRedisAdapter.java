package com.serverbe.adapter.out.persistence.art;

import com.serverbe.application.port.out.art.RunningArtRedisPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Redis GEO 자료구조를 활용한 런닝 아트 공간 데이터 어댑터
 */
@Component
@RequiredArgsConstructor
public class RunningArtRedisAdapter implements RunningArtRedisPort {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @Value("${redis.keys.running-art-geo}")
    private String geoKey;

    /**
     * 런닝 아트의 위치 좌표를 Redis GEO 인덱스에 저장 또는 갱신합니다.
     *
     * @param id  런닝 아트 식별자 ID
     * @param lat 위도 (Latitude)
     * @param lon 경도 (Longitude)
     * @return 추가/갱신 성공 여부를 반환하는 Mono
     */
    @Override
    public Mono<Long> saveLocation(Long id, Double lat, Double lon) {
        return reactiveRedisTemplate.opsForGeo()
                .add(geoKey, new Point(lon, lat), id.toString());
    }

    /**
     * 중심 좌표를 기준으로 특정 반경 내에 있는 런닝 아트 ID 목록을 조회합니다.
     *
     * @param userLat  중심점 위도
     * @param userLon  중심점 경도
     * @param radiusKm 검색 반경 크기 (km)
     * @return 반경 내 검색된 런닝 아트 ID를 거리 오름차순으로 방출하는 Flux
     */
    @Override
    public Flux<Long> findNearbyIds(Double userLat, Double userLon, Double radiusKm) {
        Circle circle = new Circle(
                new Point(userLon, userLat),
                new Distance(radiusKm, Metrics.KILOMETERS)
        );

        return reactiveRedisTemplate.opsForGeo()
                .radius(
                        geoKey,
                        circle,
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().sortAscending()
                )
                .map(result -> Long.parseLong(result.getContent().getName()));
    }

    /**
     * 특정 런닝 아트의 위치 데이터를 Redis GEO 인덱스에서 제거합니다.
     *
     * @param id 삭제할 런닝 아트 식별자 ID
     * @return 삭제 처리된 데이터 건수를 반환하는 Mono
     */
    @Override
    public Mono<Long> removeLocation(Long id) {
        return reactiveRedisTemplate.opsForGeo()
                .remove(geoKey, id.toString());
    }

    /**
     * Redis GEO 인덱스에 저장된 모든 위치 데이터를 초기화(전체 삭제)합니다.
     *
     * @return 삭제 성공 여부를 반환하는 Mono (true: 삭제됨, false: 삭제 실패 또는 데이터 없음)
     */
    @Override
    public Mono<Boolean> clearAllLocations() {
        return reactiveRedisTemplate.delete(geoKey)
                .map(count -> count > 0)
                .defaultIfEmpty(false);
    }
}