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
 * @author Duskafka
 * @responsibility 런닝 아트 관리 사용사례를 구현한다.
 * @see GetRunningArtUseCase
 * @see DeleteRunningArtUseCase
 * @see UpdateRunningArtUseCase
 */
@Service
@RequiredArgsConstructor
public class RunningArtService implements GetRunningArtUseCase, DeleteRunningArtUseCase, UpdateRunningArtUseCase {
    private final RunningArtRepositoryPort repositoryPort;

    /**
     * @return 런닝 아트 정보 DTO
     * @implNote 사용자의 ID로 런닝 아트를 조회하고 일치하는 항목들만 가져온다.
     * @implSpec 조회에 순수 JPA만 사용한다. 추후 필요하다면 Querydsl의 Projection 기능을 사용해서 필요한 필드만 가져올 수 있도록 구현해야 한다.
     * @requirement UC-ART-03: 사용자의 전체 런닝 아트 조회 요청
     */
    @Override
    @Transactional(readOnly = true)
    public List<RunningArtResult> getRunningArtsByUserId(Long userId) {
        return repositoryPort.findByUserId(userId).stream().map(RunningArtResult::toResult).toList();
    }

    /**
     * @param userId       사용자의 것인지 검증하기 위한 PK
     * @param runningArtId 조회할 런닝 아트의 PK
     * @return 조회한 런닝 아트 데이터
     * @implSpec 조회는 {@link #findAndVerifyOwner(Long, Long)}에서 조회하고 소유자가 일치하는지 검증한다.
     * @responsibility 사용자의 런닝 아트를 조회한다.
     * @requirement UC-ART-02: 런닝 아트 단건 조회 요청
     */
    @Override
    @Transactional(readOnly = true)
    public RunningArtResult getRunningArtById(Long userId, Long runningArtId) {
        return RunningArtResult.toResult(findAndVerifyOwner(userId, runningArtId));
    }

    /**
     * @param userId       수정 요청한 사용자의 ID
     * @param runningArtId 수정할 런닝 아트의 ID
     * @param command      수정할 내용을 담고있는 DTO
     * @responsibility 런닝 아트를 수정하는 책임
     * @implSpec 조회는 {@link #findAndVerifyOwner(Long, Long)}에서 조회하고 소유자가 일치하는지 검증한다. 그리고 일치할 때 수정한다.
     * @requirement UC-ART-04: 런닝 아트 수정 요청
     */
    @Override
    @Transactional
    public void updateRunningArt(Long userId, Long runningArtId, RunningArtUpdateCommand command) {
        findAndVerifyOwner(userId, runningArtId);

        repositoryPort.updateMetadata(runningArtId, command);
    }

    /**
     * @param userId       삭제를 요청한 사용자의 ID
     * @param runningArtId 삭제할 런닝 아트의 ID
     * @implSpec 조회는 {@link #findAndVerifyOwner(Long, Long)}에서 조회하고 소유자가 일치하는지 검증한다. 그리고 일치할 때 삭제한다.
     * @responsibility 런닝 아트를 삭제한다.
     * @requirement UC-ART-05 런닝 아트 삭제 요청
     */
    @Override
    @Transactional
    public void deleteRunningArt(Long userId, Long runningArtId) {
        findAndVerifyOwner(userId, runningArtId);

        repositoryPort.deleteById(runningArtId);
    }

    /**
     * @param userId 런닝 아트를삭제할 사용자의 ID
     * @responsibility 사용자의 모든 런닝 아트를 삭제한다.
     * @requirement UC-ART-06: 사용자 전체 런닝 아트 삭제 요청
     */
    @Override
    @Transactional
    public void deleteAllRunningArtsByUserId(Long userId) {
        repositoryPort.deleteByUserId(userId);
    }

    /**
     * {@code RunningArt} 엔티티를 찾고 소유자가 맞는지 검증하는 메소드.
     *
     * @param userId       사용자의 ID (PK)
     * @param runningArtId 조회할 {@code RunningArt}의 ID (PK)
     * @return {@code RunningArt} 엔티티 자체를 리턴합니다.
     * @throws BusinessException 런닝 아트를 조회하지 못하거나 조회를 요청한 사람에게 소유권이 없을 때 예외가 발생함.
     * @implNote {@code RunningArt} 엔티티를 그대로 응답하기 때문에 절대 외부에서 사용하면 안됨.
     * @implNote {@code @Transactional} 애노테이션 내부에서 사용하지 않으면 오류 발생
     * @responsibility
     */
    private RunningArt findAndVerifyOwner(Long userId, Long runningArtId) {
        RunningArt runningArt = repositoryPort.findById(runningArtId)
                .orElseThrow(() -> new BusinessException(
                        ErrorMessage.NOT_FOUND_RUNNING_ART,
                        String.format("런닝아트(%d)를 찾지 못했습니다.", runningArtId))
                );
        if (!runningArt.getUserId().equals(userId)) {
            throw new BusinessException(
                    ErrorMessage.USER_IS_NOT_OWNER_OF_RUNNING_ART,
                    String.format("사용자(ID: %d)는 해당 런닝아트(ID: %d)에 대한 권한이 없습니다.", userId, runningArtId)
            );
        }
        return runningArt;
    }
}