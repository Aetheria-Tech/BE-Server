package com.serverbe.adapter.in.web.dto.me;

import com.serverbe.application.port.out.dto.me.UserProfileResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 프로필 조회 응답")
public record UserProfileResponse(
        @Schema(description = "사용자 이메일", example = "runner@example.com")
        String email,

        @Schema(description = "닉네임", example = "달리는심장")
        String nickname,

        @Schema(description = "상태 메시지 (한 줄 소개)", example = "매일 아침 7시, 한강을 달립니다. 🏃‍♂️")
        String statusMessage
) {
    /**
     * 비즈니스 결과 객체(Result)를 웹 응답 객체(Response)로 변환합니다.
     */
    public static UserProfileResponse toResponse(UserProfileResult result) {
        return new UserProfileResponse(
                result.email(),
                result.nickname(),
                result.statusMessage()
        );
    }
}