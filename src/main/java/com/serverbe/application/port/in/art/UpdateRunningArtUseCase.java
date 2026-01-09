package com.serverbe.application.port.in.art;

import com.serverbe.application.port.in.dto.art.RunningArtUpdateCommand;

public interface UpdateRunningArtUseCase {
    /**
     * @param userId       수정 요청한 사용자의 ID
     * @param runningArtId 수정할 런닝 아트의 ID
     * @param command      수정할 내용을 담고있는 DTO
     * @responsibility 런닝 아트를 수정하는 책임
     * @requirement UC-ART-04: 런닝 아트 수정 요청
     */
    void updateRunningArt(Long userId, Long runningArtId, RunningArtUpdateCommand command);
}