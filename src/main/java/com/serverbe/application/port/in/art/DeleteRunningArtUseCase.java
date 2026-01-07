package com.serverbe.application.port.in.art;

public interface DeleteRunningArtUseCase {
    void deleteRunningArt(Long userId, Long runningArtId);
    void deleteAllRunningArtsByUserId(Long userId);
}