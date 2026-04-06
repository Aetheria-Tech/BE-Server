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

/**
 * @responsibility Redis의 GEO(지리적 데이터) 구조를 활용하여 런닝 아트의 위치 정보를 저장하고 반경 기반 탐색을 수행하는 어댑터입니다.
 * @implSpec {@link ReactiveRedisTemplate}을 주입받아 논블로킹(Non-blocking) 방식으로 Redis와 통신하며, {@link RunningArtRedisPort}를 구현합니다.
 * @implNote 데이터는 "running_art:locations"라는 단일 키(Sorted Set 기반)에 저장되며, 식별자는 String 형태로 변환되어 관리됩니다.
 */
@Component
@RequiredArgsConstructor
public class RunningArtRedisAdapter implements RunningArtRedisPort {
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private static final String GEO_KEY = "running_art:locations";

    /**
     * @param id 런닝 아트의 고유 식별자
     * @param lat 위도 (y 좌표)
     * @param lon 경도 (x 좌표)
     * @return 저장 성공 여부를 나타내는 {@link Mono<Long>}
     * @responsibility Redis GEOADD 명령어를 사용하여 좌표와 ID를 매핑하여 저장합니다.
     * @implSpec {@link Point} 객체 생성 시 반드시 (경도, 위도) 순서로 파라미터를 전달해야 하며, 식별자는 {@code String}으로 변환하여 저장합니다.
     */
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
     * @responsibility Redis GEOSEARCH 명령어를 사용하여 특정 반경 내의 데이터를 거리 기준으로 오름차순(가까운 순) 정렬하여 조회합니다.
     * @implSpec 반환된 Redis 결과 객체에서 {@code Name(String)} 속성을 추출한 후 {@code Long} 타입으로 파싱하여 스트리밍합니다.
     */
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