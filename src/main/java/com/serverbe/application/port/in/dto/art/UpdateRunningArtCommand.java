package com.serverbe.application.port.in.dto.art;

import jakarta.validation.constraints.NotBlank;

public record UpdateRunningArtCommand(
        @NotBlank
        String title,

        @NotBlank
        String content
) {
    // 필요 시 유효성 검증(Validation) 로직 추가
}