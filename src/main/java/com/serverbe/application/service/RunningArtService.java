package com.serverbe.application.service;

import com.serverbe.application.port.in.art.*;
import com.serverbe.application.port.in.dto.art.RunningArtResult;
import com.serverbe.application.port.in.dto.art.RunningArtUpdateCommand;
import com.serverbe.application.port.out.art.RunningArtRedisPort;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
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
        GetRunningArtUseCase,
        DeleteRunningArtUseCase,
        UpdateRunningArtUseCase,
        GetNearbyRunningArtUseCase,
        RegisterCompletedArtUseCase
{

    private final RunningArtRepositoryPort repositoryPort;
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

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runningArtRedisPort.removeLocation(runningArtId)
                        .subscribe(
                                result -> log.info("Redis GEO 삭제 완료 (ArtId: {})", runningArtId),
                                error -> log.error("Redis GEO 삭제 실패 (ArtId: {}): {}", runningArtId, error.getMessage())
                        );
            }
        });
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

    /**
     * @responsibility 비동기 AI 작업이 완료된 후, 전달받은 GPX 데이터를 바탕으로 런닝아트를 생성하고 DB/Redis에 등록합니다.
     */
    @Override
    @Transactional
    public Long registerFromPolyline(Long userId, String polyline, String title, String shape, Proficiency proficiency) {

        // 1. Polyline에서 메타데이터(시작 좌표, 거리 등) 추출
        PolylineUtils.PolylineMetadata metadata = PolylineUtils.extractMetadata(polyline);

        // 2. 도메인 엔티티 조립
        RunningArt runningArt = RunningArt.builder()
                .userId(userId)
                .title(title)
                .gpx(polyline) // 변수명은 gpx지만 실제론 polyline 데이터가 들어갑니다.
                .content("AI 생성 런닝 아트")
                .shape(shape)
                .proficiency(proficiency)
                .distance(metadata.totalDistanceMeters())
                .startLat(metadata.startLat())
                .startLon(metadata.startLon())
                .build();

        // 3. DB 저장 (JPA Blocking 방식)
        RunningArt savedArt = repositoryPort.save(runningArt);
        log.info("DB 런닝아트 저장 완료 (ArtId: {})", savedArt.id());

        // 4. Redis 동기화 (기존 비동기 코드를 여기서만 살짝 block 해줍니다)
        try {
            runningArtRedisPort.saveLocation(savedArt.id(), savedArt.startLat(), savedArt.startLon())
                    .block(); // ✨ SQS 리스너 워커 스레드이므로 block() 해도 전혀 문제없습니다!
            log.info("Redis 동기화 완료 (ArtId: {})", savedArt.id());
        } catch (Exception e) {
            // Redis 저장이 실패해도 DB 저장을 롤백시키지 않으려면 try-catch로 감싸줍니다.
            log.error("Redis GEO 동기화 실패 (ArtId={}): 나중에 배치로 복구해야 합니다.", savedArt.id(), e);
        }

        // 5. 최종 ID 반환!
        return savedArt.id();
    }
}