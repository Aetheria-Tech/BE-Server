package com.serverbe.application.port.out.dto.me;

import com.serverbe.domain.model.user.User;

/**
 * @responsibility 사용자의 프로필 정보를 외부 계층에 전달하는 불변 객체입니다.
 * @param email 사용자 이메일
 * @param nickname 사용자 닉네임
 * @param statusMessage 사용자의 상태 메시지
 */
public record UserProfileResult(
        String email,
        String nickname,
        String statusMessage
) {
    /**
     * @responsibility {@link User} 도메인 모델을 UserProfileResult DTO로 변환합니다.
     * @param user 변환할 사용자 도메인 엔티티
     * @return 프로필 정보가 담긴 DTO 객체
     */
    public static UserProfileResult from(User user) {
        return new UserProfileResult(user.email(), user.nickname(), user.statusMessage());
    }
}