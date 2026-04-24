package com.serverbe.adapter.in.web.dto.art;

import com.serverbe.domain.model.art.vo.Proficiency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record CreateRunningArtRequest(

        @NotBlank(message = "시작 위치는 필수 입력값입니다.")
        String startPosition,

        @NotBlank(message = "런닝 코스 모양은 필수 입력값입니다.")
        String shape,

        @NotNull(message = "러닝 숙련도는 필수 입력값입니다.")
        Proficiency proficiency
) {
}