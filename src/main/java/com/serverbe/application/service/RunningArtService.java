package com.serverbe.application.service;

import com.serverbe.application.port.in.art.GetRunningArtUseCase;
import com.serverbe.application.port.in.art.DeleteRunningArtUseCase;
import com.serverbe.application.port.in.art.UpdateRunningArtUseCase;
import com.serverbe.application.port.in.dto.art.RunningArtResult;
import com.serverbe.application.port.in.dto.art.RunningArtUpdateCommand;
import com.serverbe.application.port.out.jpa.RunningArtRepositoryPort;
import com.serverbe.domain.model.art.RunningArt;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @responsibility 사용자가 생성한 런닝 아트(GPS 궤적 데이터 기반 기록)의 생명주기를 관리하고 접근 권한을 제어합니다.
 * @implSpec {@link GetRunningArtUseCase}, {@link DeleteRunningArtUseCase}, {@link UpdateRunningArtUseCase}를 모두 구현하는 통합 관리 서비스입니다.
 */
@Service
@RequiredArgsConstructor
public class RunningArtService implements GetRunningArtUseCase, DeleteRunningArtUseCase, UpdateRunningArtUseCase {
    private final RunningArtRepositoryPort repositoryPort;

    /**
     * @requirement UC-ART-03: 사용자의 전체 런닝 아트 조회 요청
     * @responsibility 특정 사용자가 보유한 모든 런닝 아트 목록을 조회합니다.
     * @implSpec
     * 1. {@link RunningArtRepositoryPort#findByUserId(Long)}를 통해 해당 유저의 엔티티 목록을 획득합니다.<br>
     * 2. Java Stream API를 활용하여 도메인 모델을 {@link RunningArtResult} DTO로 일괄 변환합니다.
     * @implNote 현재 순수 JPA로 구현되어 있으며, 대량 데이터 조회 시 성능 최적화가 필요할 경우 Querydsl Projection 도입을 고려해야 합니다.
     * @param userId 런닝 아트를 조회할 사용자의 고유 식별자
     * @return 사용자의 런닝 아트 정보 리스트
     */
    @Override
    @Transactional(readOnly = true)
    public List<RunningArtResult> getRunningArtsByUserId(Long userId) {
        return repositoryPort.findByUserId(userId).stream().map(RunningArtResult::toResult).toList();
    }

    /**
     * @requirement UC-ART-02: 런닝 아트 단건 조회 요청
     * @responsibility 특정 런닝 아트의 상세 정보를 조회하며, 요청자에게 소유권이 있는지 검증합니다.
     * @implSpec {@link #findAndVerifyOwner(Long, Long)}를 호출하여 데이터 존재 여부와 접근 권한을 동시에 확인합니다.
     * @param userId 사용자의 고유 식별자 (소유권 검증용)
     * @param runningArtId 조회할 런닝 아트의 고유 식별자
     * @return 조회된 런닝 아트 상세 데이터 {@link RunningArtResult}
     */
    @Override
    @Transactional(readOnly = true)
    public RunningArtResult getRunningArtById(Long userId, Long runningArtId) {
        return RunningArtResult.toResult(findAndVerifyOwner(userId, runningArtId));
    }

    /**
     * @requirement UC-ART-04: 런닝 아트 수정 요청
     * @responsibility 런닝 아트의 메타데이터(제목, 설명 등)를 수정합니다.
     * @implSpec
     * 1. {@link #findAndVerifyOwner(Long, Long)}로 권한을 확인합니다.<br>
     * 2. {@link RunningArtRepositoryPort#updateMetadata(Long, RunningArtUpdateCommand)}를 호출하여 변경 사항을 영속성 계층에 반영합니다.
     * @param userId 수정 요청자의 고유 식별자
     * @param runningArtId 수정 대상 런닝 아트의 고유 식별자
     * @param command 수정한 메타데이터 정보가 담긴 DTO
     */
    @Override
    @Transactional
    public void updateRunningArt(Long userId, Long runningArtId, RunningArtUpdateCommand command) {
        findAndVerifyOwner(userId, runningArtId);

        repositoryPort.updateMetadata(runningArtId, command);
    }

    /**
     * @requirement UC-ART-05 런닝 아트 삭제 요청
     * @responsibility 특정 런닝 아트를 영구 삭제합니다.
     * @implSpec {@link #findAndVerifyOwner(Long, Long)}를 통해 본인의 기록임이 확인된 경우에만 삭제 명령을 수행합니다.
     * @param userId 삭제 요청자의 고유 식별자
     * @param runningArtId 삭제할 런닝 아트의 고유 식별자
     */
    @Override
    @Transactional
    public void deleteRunningArt(Long userId, Long runningArtId) {
        findAndVerifyOwner(userId, runningArtId);

        repositoryPort.deleteById(runningArtId);
    }

    /**
     * @requirement UC-ART-06: 사용자 전체 런닝 아트 삭제 요청
     * @responsibility 특정 사용자와 연관된 모든 런닝 아트 데이터를 삭제합니다. 주로 회원 탈퇴 시 호출됩니다.
     * @param userId 모든 데이터를 삭제할 사용자의 고유 식별자
     */
    @Override
    @Transactional
    public void deleteAllRunningArtsByUserId(Long userId) {
        repositoryPort.deleteByUserId(userId);
    }

    /**
     * @responsibility 데이터의 존재 유무를 확인하고, 요청한 사용자가 해당 데이터의 실제 소유자인지 검증합니다.
     * @implSpec
     * 1. ID로 엔티티를 조회하며, 부재 시 {@link ErrorMessage#NOT_FOUND_RUNNING_ART} 예외를 던집니다.<br>
     * 2. 조회된 엔티티의 {@code userId} 필드와 요청자의 {@code userId}를 비교하여 불일치 시 {@link ErrorMessage#USER_IS_NOT_OWNER_OF_RUNNING_ART} 예외를 던집니다.
     * @implNote
     * - 이 메서드는 도메인 엔티티({@link RunningArt})를 직접 반환하므로 서비스 내부 전용(private)으로만 사용해야 합니다.<br>
     * - 더티 체킹이나 연관 관계 접근을 위해 반드시 {@link Transactional} 컨텍스트 내에서 호출되어야 합니다.
     * @param userId 검증할 사용자의 ID
     * @param runningArtId 검증 대상 런닝 아트 ID
     * @return 검증이 완료된 {@link RunningArt} 엔티티
     * @throws BusinessException 데이터가 없거나 소유권이 없는 경우 발생
     */
    private RunningArt findAndVerifyOwner(Long userId, Long runningArtId) {
        RunningArt runningArt = repositoryPort.findById(runningArtId)
                .orElseThrow(() -> new BusinessException(
                        ErrorMessage.NOT_FOUND_RUNNING_ART,
                        String.format("런닝아트(%d)를 찾지 못했습니다.", runningArtId))
                );
        if (!runningArt.userId().equals(userId)) {
            throw new BusinessException(
                    ErrorMessage.USER_IS_NOT_OWNER_OF_RUNNING_ART,
                    String.format("사용자(ID: %d)는 해당 런닝아트(ID: %d)에 대한 권한이 없습니다.", userId, runningArtId)
            );
        }
        return runningArt;
    }
}