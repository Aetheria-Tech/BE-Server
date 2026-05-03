package com.serverbe.application.port.in.me;


import com.serverbe.application.port.out.dto.me.UserProfileResult;

public interface GetUserUseCase {
    /**
     * @param userId 조회할 사용자의 ID
     * @return 사용자를 조회하고 외부에 노출해도 되는 정보 (이메일, 닉네임, 상태 메시지)만 매핑하여 외부에 응답함.
     * @responsibility 사용자의 회원 정보를 조회하는 책임
     * @requirement UC-USER-01: 사용자 정보 조회
     */
    UserProfileResult getMyProfile(Long userId);
}