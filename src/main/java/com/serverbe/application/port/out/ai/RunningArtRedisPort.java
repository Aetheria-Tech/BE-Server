package com.serverbe.application.port.out.ai;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @responsibility 런닝 아트의 위치 기반 공간 데이터(Spatial Data)를 관리하기 위한 아웃바운드 포트입니다.
 * @implSpec 이 포트의 구현체는 메모리 기반의 데이터 스토어(예: Redis GEO)를 사용하여 고속의 반경 검색과 위치 저장을 지원해야 합니다.
 * @implNote 모든 반환 타입은 Project Reactor 기반의 {@link Mono}와 {@link Flux}를 사용하여 논블로킹 통신을 보장합니다.
 */
public interface RunningArtRedisPort {

    /**
     * @param id 저장할 런닝 아트의 고유 식별자
     * @param lat 위도 (Latitude)
     * @param lon 경도 (Longitude)
     * @return 저장 작업의 결과를 나타내는 {@link Mono<Long>} (성공 시 추가된 요소의 개수 등 반환 가능)
     * @responsibility 새로운 런닝 아트의 고유 ID와 시작점 좌표를 공간 인덱스에 등록합니다.
     */
    Mono<Long> saveLocation(Long id, Double lat, Double lon);

    /**
     * @param userLat 검색 기준이 되는 중심점의 위도
     * @param userLon 검색 기준이 되는 중심점의 경도
     * @param radiusKm 검색 반경 (킬로미터 단위)
     * @return 중심점으로부터 지정된 반경 내에 존재하는 런닝 아트의 고유 ID 목록을 스트리밍하는 {@link Flux<Long>}
     * @responsibility 주어진 좌표와 반경 정보를 바탕으로 주변에 있는 런닝 아트의 ID를 탐색하여 거리순으로 반환합니다.
     */
    Flux<Long> findNearbyIds(Double userLat, Double userLon, Double radiusKm);

    Mono<Long> removeLocation(Long id);
}