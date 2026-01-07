package com.serverbe.application.port.in.art;

import com.serverbe.application.port.in.dto.art.RunningArtResult;
import com.serverbe.domain.model.art.RunningArt;

import java.util.List;

public interface GetRunningArtUseCase {
    // 사용자 ID로 사용자의 런닝 아트 조회
    List<RunningArtResult> getRunningArtsByUserId(Long userId);

    // 런닝 아트 ID로 런닝 아트 조회
    RunningArtResult getRunningArtById(Long userId, Long runningArtId);
}