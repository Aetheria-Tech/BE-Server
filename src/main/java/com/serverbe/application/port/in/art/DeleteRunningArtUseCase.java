package com.serverbe.application.port.in.art;

public interface DeleteRunningArtUseCase {
    /**
     * @param userId       삭제를 요청한 사용자의 ID
     * @param runningArtId 삭제할 런닝 아트의 ID
     * @responsibility 런닝 아트를 삭제한다.
     * @requirement UC-ART-05 런닝 아트 삭제 요청
     */
    void deleteRunningArt(Long userId, Long runningArtId);

    /**
     * @param userId 런닝 아트를삭제할 사용자의 ID
     * @responsibility 사용자의 모든 런닝 아트를 삭제한다.
     * @requirement UC-ART-06: 사용자 전체 런닝 아트 삭제 요청
     */
    void deleteAllRunningArtsByUserId(Long userId);
}