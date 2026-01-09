package com.serverbe.application.port.in.me;

import com.serverbe.application.port.out.dto.me.UserProfileResult;
import com.serverbe.application.port.out.dto.me.UserUpdateCommand;

public interface UpdateUserUseCase {
    /**
     * @param userId  수정할 사용자의 ID
     * @param command 수정할 정보를 담은 DTO
     * @return 사용자 정보를 수정하고 외부에 노출하도 되는 정보 (이메일, 닉네임, 상태 메시지)만 매핑하여 외부에 응답한다.
     * @responsibility 사용자의 정보를 수정하는 책임
     * @requirement UC-USER-02: 사용자 정보 수정
     */
    UserProfileResult updateMyProfile(Long userId, UserUpdateCommand command);
}