package com.serverbe.application.service;

import com.serverbe.application.port.in.art.GetRunningArtQuery;
import com.serverbe.application.port.in.art.ManageRunningArtUseCase;
import com.serverbe.application.port.in.dto.art.UpdateRunningArtCommand;
import com.serverbe.application.port.out.dto.art.RunningArtUpdateDto;
import com.serverbe.application.port.out.jpa.RunningArtRepositoryPort;
import com.serverbe.domain.model.art.RunningArt;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class RunningArtService implements GetRunningArtQuery, ManageRunningArtUseCase {
    private final RunningArtRepositoryPort repositoryPort;


    @Override
    @Transactional(readOnly = true)
    public List<RunningArt> getRunningArtsByUserId(Long userId) {
        return repositoryPort.findByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public RunningArt getRunningArtById(Long id) {
        return repositoryPort.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("해당 작품을 찾을 수 없습니다. ID: " + id));
    }


    @Override
    public void deleteRunningArt(Long id) {
        repositoryPort.deleteById(id);
    }

    @Override
    public void updateRunningArt(Long id, UpdateRunningArtCommand command) {
        // Command를 DTO로 변환하여 출력 포트에 전달
        RunningArtUpdateDto updateDto = new RunningArtUpdateDto(command.title(), command.content());
        repositoryPort.updateMetadata(id, updateDto);
    }

    @Override
    public void deleteAllRunningArtsByUserId(Long userId) {
        repositoryPort.deleteByUserId(userId);
    }
}