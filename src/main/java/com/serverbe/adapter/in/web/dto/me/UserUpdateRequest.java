package com.serverbe.adapter.in.web.dto.me;

import com.serverbe.application.port.out.dto.me.UserUpdateCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "내 프로필 수정 요청")
public record UserUpdateRequest(
        @Schema(description = "변경할 닉네임", example = "러닝마스터")
        @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하로 입력해주세요.")
        String nickname,

        @Schema(description = "변경할 상태 메시지", example = "오늘도 달립니다!")
        @Size(max = 100, message = "상태 메시지는 100자 이하로 입력해주세요.")
        String statusMessage
) {
    /**
     * Web 계층의 DTO를 Application 계층의 Command로 변환합니다.
     */
    public UserUpdateCommand toCommand() {
        return new UserUpdateCommand(
                this.nickname,
                this.statusMessage
        );
    }
}