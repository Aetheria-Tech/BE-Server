package com.serverbe.application.port.in.art;

import com.serverbe.application.port.in.dto.art.RunningArtUpdateCommand;

public interface UpdateRunningArtUseCase {
    void updateRunningArt(Long userId, Long runningArtId, RunningArtUpdateCommand command);
}