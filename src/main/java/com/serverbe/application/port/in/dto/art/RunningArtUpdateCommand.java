package com.serverbe.application.port.in.dto.art;

public record RunningArtUpdateCommand(
        String title,
        String content
) {
}