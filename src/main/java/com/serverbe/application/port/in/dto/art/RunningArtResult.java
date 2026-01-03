package com.serverbe.application.port.in.dto.art;

import com.serverbe.domain.model.art.RunningArt;
import com.serverbe.domain.model.art.vo.Proficiency;
import lombok.Builder;

@Builder
public record RunningArtResult(
        Long id,
        String title,
        String content,
        String shape,
        Proficiency proficiency,
        String gpx,
        Long userId
) {
    public static RunningArtResult toResult(RunningArt runningArt){
        return RunningArtResult.builder()
                .id(runningArt.getId())
                .title(runningArt.getTitle())
                .content(runningArt.getContent())
                .shape(runningArt.getShape())
                .proficiency(runningArt.getProficiency())
                .gpx(runningArt.getGpx())
                .userId(runningArt.getUserId())
                .build();
    }
}