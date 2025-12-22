package com.serverbe.application.port.out.jpa;

import com.serverbe.application.port.out.dto.art.RunningArtUpdateDto;
import com.serverbe.domain.model.art.RunningArt;

import java.util.List;
import java.util.Optional;

public interface RunningArtRepositoryPort {
    RunningArt save(RunningArt runningArt);
    Optional<RunningArt> findById(Long id);
    List<RunningArt> findByUserId(Long userId);
    List<RunningArt> findAll();
    void deleteById(Long id);
    void deleteByUserId(Long userId);
    void updateMetadata(Long id, RunningArtUpdateDto dto);
}