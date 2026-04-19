package com.serverbe.application.service;

import com.serverbe.application.port.in.art.*;
import com.serverbe.application.port.in.dto.art.RunningArtResult;
import com.serverbe.application.port.in.dto.art.RunningArtUpdateCommand;
import com.serverbe.application.port.out.sagemaker.RunningArtAIPort;
import com.serverbe.application.port.out.art.RunningArtRedisPort;
import com.serverbe.application.port.out.geocode.GeocodePort;
import com.serverbe.application.port.out.jpa.RunningArtRepositoryPort;
import com.serverbe.domain.exception.art.ArtErrorCode;
import com.serverbe.domain.exception.art.ArtException;
import com.serverbe.domain.model.art.RunningArt;
import com.serverbe.domain.exception.BusinessException;
import com.serverbe.domain.model.art.vo.Proficiency;
import com.serverbe.infrastructure.util.PolylineUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @responsibility 사용자가 생성한 런닝 아트(GPS 궤적 데이터 기반 기록)의 생명주기를 관리하고 접근 권한을 제어합니다.
 * @implSpec {@link GetRunningArtUseCase}, {@link DeleteRunningArtUseCase}, {@link UpdateRunningArtUseCase}를 모두 구현하는 통합 관리 서비스입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RunningArtService implements
        GetRunningArtUseCase, DeleteRunningArtUseCase, UpdateRunningArtUseCase, CreateRunningArtUseCase, GetNearbyRunningArtUseCase {

    private final RunningArtRepositoryPort repositoryPort;
    private final RunningArtAIPort runningArtAIPort;
    private final GeocodePort geocodePort;
    private final RunningArtRedisPort runningArtRedisPort;

    /**
     * @param userId   런닝 아트를 조회할 사용자의 고유 식별자
     * @param pageable 페이징 정보
     * @return 사용자의 런닝 아트 정보 페이지
     * @requirement UC-ART-03: 사용자의 전체 런닝 아트 조회 요청
     * @responsibility 특정 사용자가 보유한 모든 런닝 아트 목록을 조회합니다.
     * @implSpec 1. {@link RunningArtRepositoryPort#findByUserId(Long, Pageable)}를 통해 해당 유저의 엔티티 목록을 획득합니다.<br>
     * 2. {@link Page#map(java.util.function.Function)}을 활용하여 도메인 모델을 {@link RunningArtResult} DTO로 일괄 변환합니다.
     * @implNote 현재 순수 JPA로 구현되어 있으며, 대량 데이터 조회 시 성능 최적화가 필요할 경우 Querydsl Projection 도입을 고려해야 합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<RunningArtResult> getRunningArtsByUserId(Long userId, Pageable pageable) {
        return repositoryPort.findByUserId(userId, pageable).map(RunningArtResult::toResult);
    }

    /**
     * @param userId       사용자의 고유 식별자 (소유권 검증용)
     * @param runningArtId 조회할 런닝 아트의 고유 식별자
     * @return 조회된 런닝 아트 상세 데이터 {@link RunningArtResult}
     * @requirement UC-ART-02: 런닝 아트 단건 조회 요청
     * @responsibility 특정 런닝 아트의 상세 정보를 조회하며, 요청자에게 소유권이 있는지 검증합니다.
     * @implSpec {@link #findAndVerifyOwner(Long, Long)}를 호출하여 데이터 존재 여부와 접근 권한을 동시에 확인합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public RunningArtResult getRunningArtById(Long userId, Long runningArtId) {
        return RunningArtResult.toResult(findAndVerifyOwner(userId, runningArtId));
    }

    /**
     * @param userId       수정 요청자의 고유 식별자
     * @param runningArtId 수정 대상 런닝 아트의 고유 식별자
     * @param command      수정한 메타데이터 정보가 담긴 DTO
     * @requirement UC-ART-04: 런닝 아트 수정 요청
     * @responsibility 런닝 아트의 메타데이터(제목, 설명 등)를 수정합니다.
     * @implSpec 1. {@link #findAndVerifyOwner(Long, Long)}로 권한을 확인합니다.<br>
     * 2. {@link RunningArtRepositoryPort#updateMetadata(Long, RunningArtUpdateCommand)}를 호출하여 변경 사항을 영속성 계층에 반영합니다.
     */
    @Override
    @Transactional
    public void updateRunningArt(Long userId, Long runningArtId, RunningArtUpdateCommand command) {
        findAndVerifyOwner(userId, runningArtId);

        repositoryPort.updateMetadata(runningArtId, command);
    }

    /**
     * @param userId       삭제 요청자의 고유 식별자
     * @param runningArtId 삭제할 런닝 아트의 고유 식별자
     * @requirement UC-ART-05 런닝 아트 삭제 요청
     * @responsibility 특정 런닝 아트를 영구 삭제합니다.
     * @implSpec {@link #findAndVerifyOwner(Long, Long)}를 통해 본인의 기록임이 확인된 경우에만 삭제 명령을 수행합니다.
     */
    @Override
    @Transactional
    public void deleteRunningArt(Long userId, Long runningArtId) {
        findAndVerifyOwner(userId, runningArtId);

        repositoryPort.deleteById(runningArtId);

        runningArtRedisPort.removeLocation(runningArtId)
                .subscribe(
                        result -> log.info("Redis GEO 삭제 완료 (ArtId: {})", runningArtId),
                        error -> log.error("Redis GEO 삭제 실패 (ArtId: {}): {}", runningArtId, error.getMessage())
                );
    }

    /**
     * @param userId 모든 데이터를 삭제할 사용자의 고유 식별자
     * @requirement UC-ART-06: 사용자 전체 런닝 아트 삭제 요청
     * @responsibility 특정 사용자와 연관된 모든 런닝 아트 데이터를 삭제합니다. 주로 회원 탈퇴 시 호출됩니다.
     */
    @Override
    @Transactional
    public void deleteAllRunningArtsByUserId(Long userId) {
        // 1. 삭제할 모든 ID를 먼저 조회 (Redis 삭제를 위해 필요)
        List<Long> artIdsToDelete = repositoryPort.findIdsByUserId(userId);

        if (artIdsToDelete.isEmpty()) return;

        // 2. DB에서 전체 삭제 (Blocking)
        repositoryPort.deleteByUserId(userId);

        // 3. Redis에서 루프 돌며 삭제 (비동기)
        Flux.fromIterable(artIdsToDelete)
                .flatMap(runningArtRedisPort::removeLocation)
                .subscribe(
                        null, // 결과 로그는 생략 가능
                        error -> log.error("Redis 일괄 삭제 중 오류 발생 (UserId: {}): {}", userId, error.getMessage()),
                        () -> log.info("유저(ID: {})의 모든 Redis GEO 데이터 삭제 완료", userId)
                );
    }

    /**
     * @param userId       검증할 사용자의 ID
     * @param runningArtId 검증 대상 런닝 아트 ID
     * @return 검증이 완료된 {@link RunningArt} 엔티티
     * @throws BusinessException 데이터가 없거나 소유권이 없는 경우 발생
     * @responsibility 데이터의 존재 유무를 확인하고, 요청한 사용자가 해당 데이터의 실제 소유자인지 검증합니다.
     * @implSpec 1. ID로 엔티티를 조회하며, 부재 시 {@link ArtErrorCode#NOT_FOUND_RUNNING_ART} 예외를 던집니다.<br>
     * 2. 조회된 엔티티의 {@code userId} 필드와 요청자의 {@code userId}를 비교하여 불일치 시 {@link ArtErrorCode#USER_IS_NOT_OWNER_OF_RUNNING_ART} 예외를 던집니다.
     * @implNote - 이 메서드는 도메인 엔티티({@link RunningArt})를 직접 반환하므로 서비스 내부 전용(private)으로만 사용해야 합니다.<br>
     * - 더티 체킹이나 연관 관계 접근을 위해 반드시 {@link Transactional} 컨텍스트 내에서 호출되어야 합니다.
     */
    private RunningArt findAndVerifyOwner(Long userId, Long runningArtId) {
        RunningArt runningArt = repositoryPort.findById(runningArtId)
                .orElseThrow(() -> new ArtException(
                        ArtErrorCode.NOT_FOUND_RUNNING_ART,
                        String.format("런닝아트(%d)를 찾지 못했습니다.", runningArtId))
                );
        if (!runningArt.userId().equals(userId)) {
            throw new ArtException(
                    ArtErrorCode.USER_IS_NOT_OWNER_OF_RUNNING_ART,
                    String.format("사용자(ID: %d)는 해당 런닝아트(ID: %d)에 대한 권한이 없습니다.", userId, runningArtId)
            );
        }
        return runningArt;
    }

    /**
     * @param userId        생성 요청자의 고유 식별자
     * @param startPosition 런닝 아트를 시작할 위치의 주소 또는 명칭 (예: "용인 아르피아 체육공원")
     * @param shape         생성하고자 하는 런닝 아트의 모양 (예: "강아지")
     * @param proficiency   권장 런닝 난이도 및 숙련도
     * @return 생성된 런닝 아트의 상세 정보를 담은 {@link Mono<RunningArtResult>}
     * @requirement UC-ART-01: 새로운 런닝 아트 생성 및 경로 추출 요청
     * @responsibility 사용자가 입력한 시작 위치와 모양을 바탕으로 AI를 통해 런닝 코스를 생성하고, 결과를 DB와 공간 인덱스(Redis)에 동기화하여 저장합니다.
     * @implSpec 1. {@link GeocodePort}를 통해 입력된 텍스트 주소를 위경도 좌표로 변환합니다.<br>
     * 2. {@link RunningArtAIPort}를 호출하여 AI가 생성한 경로(GPX/Polyline) 데이터를 획득합니다.<br>
     * 3. {@link PolylineUtils#decodeFirstLocation(String)} 알고리즘을 사용해 인코딩된 경로에서 시작점 좌표를 고속으로 추출합니다.<br>
     * 4. 영속성 계층(DB) 저장을 위해 {@link Schedulers#boundedElastic()} 스레드 풀로 전환하여 Event Loop의 블로킹 오버헤드를 방지합니다.<br>
     * 5. DB 저장이 성공하면 {@link RunningArtRedisPort}를 호출하여 Redis GEO에 비동기(Non-blocking) 방식으로 위치 데이터를 등록합니다.
     * @implNote 외부 API 통신, 블로킹 DB I/O, 논블로킹 Redis I/O가 혼합된 복합 스트림입니다. 스레드 스위칭(`publishOn`)의 위치가 파이프라인의 성능을 결정하는 중요한 요소입니다.
     */
    @Override
    public Mono<RunningArtResult> createRunningArt(Long userId, String startPosition, String shape, Proficiency proficiency) {
        return geocodePort.geocode(startPosition)
                .flatMap(geocodeResult -> runningArtAIPort.createRunningArtGPX(
                        geocodeResult.latitude(),
                        geocodeResult.longitude(),
                        shape,
                        proficiency))
                .publishOn(Schedulers.boundedElastic())
                .map(runningArtAiResponse -> {
                    PolylineUtils.PolylineMetadata polylineMetadata = PolylineUtils.extractMetadata(runningArtAiResponse.gpx());

                    RunningArt runningArt = RunningArt.builder()
                            .userId(userId)
                            .title(startPosition + ":" + LocalDate.now())
                            .gpx(runningArtAiResponse.gpx())
                            .content("None")
                            .shape(shape)
                            .proficiency(proficiency)
                            .distance(polylineMetadata.totalDistanceMeters())
                            .startLat(polylineMetadata.startLat())
                            .startLon(polylineMetadata.startLon())
                            .build();

                    // 1. DB에 저장 (Blocking)
                    return repositoryPort.save(runningArt);
                })
                .flatMap(savedArt ->
                        runningArtRedisPort.saveLocation(savedArt.id(), savedArt.startLat(), savedArt.startLon())
                                .doOnSuccess(result -> log.info("Redis GEO 동기화 완료: {}", result))
                                .doOnError(error -> log.error("Redis GEO 동기화 실패 (ArtId={}): 나중에 배치로 복구해야 합니다.", savedArt.id(), error))
                                .onErrorResume(e -> Mono.empty())
                                .thenReturn(savedArt)
                )
                .map(RunningArtResult::toResult);
    }

    /**
     * @param lat    검색 중심점의 위도 (Latitude)
     * @param lon    검색 중심점의 경도 (Longitude)
     * @param radius 검색 반경 (단위: km)
     * @return 검색된 주변 런닝 아트 목록을 스트리밍하는 {@link Flux<RunningArtResult>}
     * @requirement UC-ART-07: 위치 기반 주변 런닝 아트 탐색 요청
     * @responsibility 주어진 좌표와 반경을 기준으로 주변에 위치한 런닝 아트를 탐색하여 반환합니다. 대용량 데이터 환경에서의 RDBMS 부하를 줄이기 위해 Redis 기반의 1차 필터링을 수행합니다.
     * @implSpec 1. {@link RunningArtRedisPort#findNearbyIds(Double, Double, Double)}를 통해 Redis GEO 공간 인덱스에서 반경 내 데이터의 고유 ID 목록을 빠르게 조회합니다.<br>
     * 2. 조회된 ID 목록이 존재할 경우, 블로킹 환경을 위한 {@link Schedulers#boundedElastic()} 스레드에서 {@link RunningArtRepositoryPort#findAllByIdIn(List)}를 호출하여 DB에서 상세 정보를 일괄(Batch) 페치합니다.
     * @implNote Redis의 {@code GEOSEARCH}는 거리순 정렬을 제공하지만, RDBMS의 {@code IN} 쿼리는 식별자 순서를 보장하지 않습니다. 만약 클라이언트에게 엄격한 거리순 반환이 요구된다면, DB 조회 이후 스트림 내에서 반환된 리스트를 재정렬하는 로직이 추가되어야 합니다.
     */
    @Override
    public Flux<RunningArtResult> getNearbyArts(Double lat, Double lon, Double radius) {
        return runningArtRedisPort.findNearbyIds(lat, lon, radius)
                .collectList()
                // 1. Reactive 방식의 빈 리스트 방어 (imperative if문 제거)
                .filter(ids -> !ids.isEmpty())
                .flatMapMany(ids ->
                        Mono.fromCallable(() -> {
                                    // 2. DB에서 엔티티 일괄 조회 (순서 보장 안 됨)
                                    List<RunningArt> arts = repositoryPort.findAllByIdIn(ids);

                                    // 3. 탐색 성능 최적화: List -> Map 변환 (O(N^2) -> O(N)으로 개선)
                                    Map<Long, RunningArt> artMap = arts.stream()
                                            .collect(Collectors.toMap(
                                                    RunningArt::id,
                                                    Function.identity(),
                                                    (existing, replacement) -> existing // 중복 ID 방어 로직
                                            ));

                                    // 4. Redis 원본 ID 배열(거리 오름차순)을 순회하며 O(1)로 꺼내오기
                                    return ids.stream()
                                            .map(artMap::get)
                                            .filter(Objects::nonNull) // DB에 없는 유령 데이터 필터링
                                            .map(RunningArtResult::toResult)
                                            .toList();
                                })
                                .subscribeOn(Schedulers.boundedElastic())
                                // 5. 완성된 List를 바로 Flux로 평탄화하여 방출
                                .flatMapIterable(Function.identity())
                );
    }
}