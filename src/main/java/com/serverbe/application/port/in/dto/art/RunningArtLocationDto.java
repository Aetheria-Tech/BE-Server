package com.serverbe.application.port.in.dto.art;

import com.querydsl.core.annotations.QueryProjection;

public record RunningArtLocationDto(
        Long id,
        Double startLat,
        Double startLon
) {
    @QueryProjection
    public RunningArtLocationDto {}
}