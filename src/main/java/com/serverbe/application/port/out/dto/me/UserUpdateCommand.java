package com.serverbe.application.port.out.dto.me;

/**
 * @responsibility 사용자 프로필 수정을 위해 입력받은 데이터를 전달하는 객체입니다.
 * @param nickname 수정할 닉네임
 * @param statusMessage 수정할 상태 메시지
 */
public record UserUpdateCommand(
        String nickname,
        String statusMessage
) {
}