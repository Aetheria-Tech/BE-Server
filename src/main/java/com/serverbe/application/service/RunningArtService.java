package com.serverbe.application.service;

import com.serverbe.application.port.in.art.CreateRunningArtUseCase;
import com.serverbe.application.port.in.art.GetRunningArtUseCase;
import com.serverbe.application.port.in.art.DeleteRunningArtUseCase;
import com.serverbe.application.port.in.art.UpdateRunningArtUseCase;
import com.serverbe.application.port.in.dto.art.RunningArtResult;
import com.serverbe.application.port.in.dto.art.RunningArtUpdateCommand;
import com.serverbe.application.port.out.ai.RunningArtAIPort;
import com.serverbe.application.port.out.dto.ai.RunningArtGPX;
import com.serverbe.application.port.out.geocode.GeocodePort;
import com.serverbe.application.port.out.jpa.RunningArtRepositoryPort;
import com.serverbe.domain.exception.art.ArtErrorCode;
import com.serverbe.domain.exception.art.ArtException;
import com.serverbe.domain.model.art.RunningArt;
import com.serverbe.domain.exception.BusinessException;
import com.serverbe.domain.model.art.vo.Proficiency;
import com.serverbe.infrastructure.util.PolylineUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;

/**
 * @responsibility 사용자가 생성한 런닝 아트(GPS 궤적 데이터 기반 기록)의 생명주기를 관리하고 접근 권한을 제어합니다.
 * @implSpec {@link GetRunningArtUseCase}, {@link DeleteRunningArtUseCase}, {@link UpdateRunningArtUseCase}를 모두 구현하는 통합 관리 서비스입니다.
 */
@Service
@RequiredArgsConstructor
public class RunningArtService implements GetRunningArtUseCase, DeleteRunningArtUseCase, UpdateRunningArtUseCase, CreateRunningArtUseCase {
    private final RunningArtRepositoryPort repositoryPort;
    private final RunningArtAIPort runningArtAIPort;
    private final GeocodePort geocodePort;

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
    }

    /**
     * @param userId 모든 데이터를 삭제할 사용자의 고유 식별자
     * @requirement UC-ART-06: 사용자 전체 런닝 아트 삭제 요청
     * @responsibility 특정 사용자와 연관된 모든 런닝 아트 데이터를 삭제합니다. 주로 회원 탈퇴 시 호출됩니다.
     */
    @Override
    @Transactional
    public void deleteAllRunningArtsByUserId(Long userId) {
        repositoryPort.deleteByUserId(userId);
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

    @Override
    public Mono<RunningArtResult> createRunningArt(Long userId, String startPosition, String shape, Proficiency proficiency) {
        return geocodePort.geocode(startPosition)
                .flatMap(geocodeResult -> runningArtAIPort.createRunningArtGPX(
                        geocodeResult.latitude(),
                        geocodeResult.longitude(),
                        shape,
                        proficiency))
                // 블로킹 작업(DB 저장)을 위해 전용 스레드 풀로 전환
                .publishOn(Schedulers.boundedElastic())
                .map(runningArtGPX -> {
                    double[] startLocation = PolylineUtils.decodeFirstLocation(runningArtGPX.gpx());
                    double startLat = startLocation[0];
                    double startLon = startLocation[1];

                    RunningArt runningArt = RunningArt.builder()
                            .userId(userId)
                            .title(startPosition + ":" + LocalDate.now())
                            .gpx(runningArtGPX.gpx())
                            .content("None")
                            .shape(shape)
                            .proficiency(proficiency)
                            .startLat(startLat)
                            .startLon(startLon)
                            .build();
                    return repositoryPort.save(runningArt);
                })
                // 다시 리액티브 타입으로 변환 및 결과 매핑
                .map(RunningArtResult::toResult);
    }
}