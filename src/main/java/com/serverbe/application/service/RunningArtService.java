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

@Service
@RequiredArgsConstructor
public class RunningArtService implements GetRunningArtUseCase, DeleteRunningArtUseCase, UpdateRunningArtUseCase {
    private final RunningArtRepositoryPort repositoryPort;


    @Override
    @Transactional(readOnly = true)
    public List<RunningArtResult> getRunningArtsByUserId(Long userId) {
        return repositoryPort.findByUserId(userId).stream().map(RunningArtResult::toResult).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RunningArtResult getRunningArtById(Long userId, Long runningArtId) {
        return RunningArtResult.toResult(findAndVerifyOwner(userId, runningArtId));
    }


    @Override
    @Transactional
    public void deleteRunningArt(Long userId, Long runningArtId) {
        findAndVerifyOwner(userId, runningArtId);

        repositoryPort.deleteById(runningArtId);
    }

    @Override
    @Transactional
    public void updateRunningArt(Long userId, Long runningArtId, RunningArtUpdateCommand command) {
        findAndVerifyOwner(userId, runningArtId);

        repositoryPort.updateMetadata(runningArtId, command);
    }

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
     * @implNote {@code RunningArt} 엔티티를 그대로 응답하기 때문에 절대 외부에서 사용하면 안됨.
     * @implNote {@code @Transactional} 애노테이션 내부에서 사용하지 않으면 오류 발생
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