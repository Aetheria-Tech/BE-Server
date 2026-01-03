package com.serverbe.adapter.in.web.dto.art;

import com.serverbe.application.port.in.dto.art.RunningArtUpdateCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "런닝 아트 수정 요청")
public record UpdateRunningArtRequest(
        @Schema(description = "수정할 아트 제목", example = "석촌호수 한바퀴 (수정)")
        @NotBlank(message = "제목은 필수입니다.")
        String title,

        @Schema(description = "수정할 아트 내용", example = "코스가 생각보다 길어서 내용을 수정합니다.")
        @NotBlank(message = "내용은 필수입니다.")
        String content
) {
    /**
     * Web 요청 객체를 비즈니스 로직용 Command 객체로 변환합니다.
     */
    public RunningArtUpdateCommand toCommand() {
        return new RunningArtUpdateCommand(title, content);
    }
}