package com.serverbe.application.port.in.art;

import com.serverbe.application.port.in.dto.art.UpdateRunningArtCommand;

public interface ManageRunningArtUseCase {
    // 3. 런닝 아트 ID로 런닝 아트 삭제
    void deleteRunningArt(Long userId, Long runningArtid);

    // 4. 런닝 아트 ID로 런닝 아트 수정
    void updateRunningArt(Long userId, Long runningAryId, UpdateRunningArtCommand command);

    // 5. 사용자 ID로 런닝 아트 삭제
    void deleteAllRunningArtsByUserId(Long userId);
}