package com.serverbe.application.port.in.art;

import com.serverbe.domain.model.art.vo.Proficiency;

public interface RegisterCompletedArtUseCase {
    // Mono를 걷어내고 순수 Long을 반환!
    Long registerFromPolyline(Long userId, String polyline, String title, String shape, Proficiency proficiency);
}