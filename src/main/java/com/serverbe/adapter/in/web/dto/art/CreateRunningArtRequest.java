package com.serverbe.adapter.in.web.dto.art;

import com.serverbe.domain.model.art.vo.Proficiency;
import lombok.Builder;

@Builder
public record CreateRunningArtRequest(
        String startPosition,
        String shape,
        Proficiency proficiency
) {
}