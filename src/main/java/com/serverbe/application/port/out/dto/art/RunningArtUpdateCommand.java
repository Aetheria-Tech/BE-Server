package com.serverbe.application.port.out.dto.art;

public record RunningArtUpdateCommand(
        String title,
        String content
) {
}