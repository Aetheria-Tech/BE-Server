package com.serverbe.application.service;

import com.serverbe.application.port.in.art.GetRunningArtUseCase;
import com.serverbe.application.port.in.art.DeleteRunningArtUseCase;
import com.serverbe.application.port.in.art.UpdateRunningArtUseCase;
import com.serverbe.application.port.in.dto.art.UpdateRunningArtCommand;
import com.serverbe.application.port.out.dto.art.RunningArtUpdateCommand;
import com.serverbe.application.port.out.jpa.RunningArtRepositoryPort;
import com.serverbe.domain.model.art.RunningArt;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class RunningArtService implements GetRunningArtUseCase, DeleteRunningArtUseCase, UpdateRunningArtUseCase {
    private final RunningArtRepositoryPort repositoryPort;


    @Override
    @Transactional(readOnly = true)
    public List<RunningArt> getRunningArtsByUserId(Long userId) {
        return repositoryPort.findByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public RunningArt getRunningArtById(Long userId, Long runningArtId) {
        return findAndVerifyOwner(userId, runningArtId);
    }


    @Override
    public void deleteRunningArt(Long userId, Long runningArtId) {
        findAndVerifyOwner(userId, runningArtId);

        repositoryPort.deleteById(runningArtId);
    }

    @Override
    public void updateRunningArt(Long userId, Long runningArtId, UpdateRunningArtCommand command) {
        findAndVerifyOwner(userId, runningArtId);

        RunningArtUpdateCommand updateDto = new RunningArtUpdateCommand(command.title(), command.content());
        repositoryPort.updateMetadata(runningArtId, updateDto);
    }

    @Override
    public void deleteAllRunningArtsByUserId(Long userId) {
        repositoryPort.deleteByUserId(userId);
    }

    private RunningArt findAndVerifyOwner(Long userId, Long runningArtId) {
        RunningArt runningArt = repositoryPort.findById(runningArtId)
                .orElseThrow(() -> new BusinessException(
                                ErrorMessage.NOT_FOUND_RUNNING_ART,
                                String.format("런닝아트(%d)를 찾지 못했습니다.", runningArtId)
                        )
                );
        if (!runningArt.getUserId().equals(userId)) {
            throw new BusinessException(ErrorMessage.USER_IS_NOT_OWNER_OF_RUNNING_ART, "사용자는 런닝아트의 주인이 아닙니다");
        }
        return runningArt;
    }
}