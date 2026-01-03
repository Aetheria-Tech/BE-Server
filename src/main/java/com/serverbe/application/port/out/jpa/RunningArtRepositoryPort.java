package com.serverbe.application.port.out.jpa;

import com.serverbe.application.port.in.dto.art.RunningArtUpdateCommand;
import com.serverbe.application.port.out.dto.art.RunningArtUpdateCommand;
import com.serverbe.domain.model.art.RunningArt;

import java.util.List;
import java.util.Optional;

public interface RunningArtRepositoryPort {
    RunningArt save(RunningArt runningArt);
    Optional<RunningArt> findById(Long id);
    List<RunningArt> findByUserId(Long userId);
    List<RunningArt> findAll();
    void deleteById(Long runningArtId);
    void deleteByUserId(Long userId);
    void updateMetadata(Long runningArtId, RunningArtUpdateCommand dto);
}