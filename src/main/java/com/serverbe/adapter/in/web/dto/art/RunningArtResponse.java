package com.serverbe.adapter.in.web.dto.art;

import com.serverbe.application.port.in.dto.art.RunningArtResult;
import com.serverbe.domain.model.art.vo.Proficiency;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record RunningArtResponse(
        @Schema(description = "런닝 아트 고유 ID", example = "1")
        Long id,

        @Schema(description = "런닝 아트 제목", example = "강남 하트 코스")
        String title,

        @Schema(description = "아트 상세 설명", example = "초보자도 따라하기 쉬운 하트 모양의 런닝 코스입니다.")
        String content,

        @Schema(description = "아트가 형상화하는 모양", example = "HEART")
        String shape,

        @Schema(description = "권장 숙련도 (BEGINNER, INTERMEDIATE, ADVANCED)", example = "BEGINNER")
        Proficiency proficiency,

        @Schema(description = "인코딩된 경로 데이터 (Google Polyline Algorithm)", example = "_p~iF~ps|U_cX?~p|U_cX?_p~iF...")
        String gpx,

        @Schema(description = "소유자 고유 ID", example = "10")
        Long userId
) {
    public static RunningArtResponse toResponse(RunningArtResult result){
        return RunningArtResponse.builder()
                .id(result.id())
                .title(result.title())
                .content(result.content())
                .shape(result.shape())
                .proficiency(result.proficiency())
                .gpx(result.gpx())
                .userId(result.userId())
                .build();
    }
}