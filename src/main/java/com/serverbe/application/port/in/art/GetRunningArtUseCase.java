package com.serverbe.application.port.in.art;

import com.serverbe.domain.model.art.RunningArt;

import java.util.List;

public interface GetRunningArtUseCase {
    // 사용자 ID로 사용자의 런닝 아트 조회
    List<RunningArt> getRunningArtsByUserId(Long userId);

    // 런닝 아트 ID로 런닝 아트 조회
    RunningArt getRunningArtById(Long userId, Long runningArtId);
}