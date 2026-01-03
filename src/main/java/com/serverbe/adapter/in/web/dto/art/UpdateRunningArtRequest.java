package com.serverbe.adapter.in.web.dto.art;

import com.serverbe.application.port.in.dto.art.UpdateRunningArtCommand;
import jakarta.validation.constraints.NotBlank;

public record UpdateRunningArtRequest(
        @NotBlank
        String title,

        @NotBlank
        String content
) {
    public UpdateRunningArtCommand toCommand() {
        return new UpdateRunningArtCommand(title, content);
    }
}