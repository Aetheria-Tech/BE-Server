package com.serverbe.application.port.out.dto.me;

/**
 * @responsibility 사용자 프로필 정보를 수정하기 위해 필요한 데이터를 전달하는 객체입니다.
 * @param nickname 변경하고자 하는 사용자의 닉네임
 * @param statusMessage 변경하고자 하는 사용자의 상태 메시지
 */
public record UserUpdateCommand(
        String nickname,
        String statusMessage
) {
}