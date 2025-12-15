package com.serverbe.application.port.in.dto;

// 수정 요청 DTO (Command)
public record UserUpdateCommand(
    String nickname,
    String profileImageUrl,
    String statusMessage
) {}