package com.serverbe.application.port.in.art;

import com.serverbe.application.port.in.dto.art.UpdateRunningArtCommand;

public interface UpdateRunningArtUseCase {
    void updateRunningArt(Long userId, Long runningArtId, UpdateRunningArtCommand command);
}