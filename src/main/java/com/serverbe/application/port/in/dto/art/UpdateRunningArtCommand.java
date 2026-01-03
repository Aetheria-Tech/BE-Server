package com.serverbe.application.port.in.dto.art;

public record UpdateRunningArtCommand(
        String title,
        String content
) {
}