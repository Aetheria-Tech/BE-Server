package com.serverbe.application.port.out.dto.me;

// 수정 요청 DTO (Command)
public record UserUpdateCommand(
    String nickname,
    String profileImageUrl,
    String statusMessage
) {
}