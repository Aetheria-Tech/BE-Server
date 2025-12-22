package com.serverbe.application.port.in.dto.art;

public record UpdateRunningArtCommand(
    String title,
    String content
) {
    // 필요 시 유효성 검증(Validation) 로직 추가
}