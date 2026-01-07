package com.serverbe.application.service;

import com.serverbe.application.port.in.me.UpdateUserUseCase;
import com.serverbe.application.port.out.dto.me.UserProfileResult;
import com.serverbe.application.port.out.dto.me.UserUpdateCommand;
import com.serverbe.application.port.in.me.GetUserUseCase;
import com.serverbe.application.port.out.jpa.UserRepositoryPort;
import com.serverbe.domain.model.user.User;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Duskafka
 * @responsibility 사용자 사용사례를 구현한다.
 * @see GetUserUseCase
 * @see UpdateUserUseCase
 */
@Service
@RequiredArgsConstructor
public class UserService implements GetUserUseCase, UpdateUserUseCase {

    private final UserRepositoryPort userRepositoryPort;

    /**
     * @return 사용자를 조회하고 외부에 노출해도 되는 정보 (이메일, 닉네임, 상태 메시지)만 매핑하여 외부에 응답함.
     * @throws BusinessException 사용자를 조회하지 못하였을 때 예외 발생
     * @responsibility 사용자의 회원 정보를 조회하는 책임
     * @implSpec {@code UserRepositoryPort} 구현체인 {@code UserPersistenceAdapter}에서 사용자 정보를 가져온 후 암호화가 이번 버전이 아니면 최신화를 하는 알고리즘이 있음
     * @implNote 데이터베이스에서 사용자 정보를 조회하고 {@code UserProfileResult} DTO로 매핑하여 응답한다.
     * @requirement UC-USER-01: 사용자 정보 조회
     * @see GetUserUseCase#getMyProfile(Long)
     */
    @Override
    @Transactional
    public UserProfileResult getMyProfile(Long userId) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorMessage.NOT_FOUND_USER,
                        String.format("사용자(ID: %d)를 찾을 수 없습니다.", userId))
                );

        return UserProfileResult.from(user);
    }

    /**
     * @return 사용자 정보를 수정하고 외부에 노출하도 되는 정보 (이메일, 닉네임, 상태 메시지)만 매핑하여 외부에 응답한다.
     * @throws BusinessException 사용자를 조회하지 못했을 때 예외 예외 발생
     * @responsibility 사용자의 정보를 수정하는 책임
     * @implSpec {@code UserRepositoryPort} 구현체인 {@code UserPersistenceAdapter}에서 사용자 정보를 가져온 후 암호화가 이번 버전이 아니면 최신화를 하는 알고리즘이 있음
     * @implNote 사용자의 정보를 수정한다. 이 과정에서 {@code User} 도메인의 업데이트 메소드를 사용한다.
     * @requirement UC-USER-02: 사용자 정보 수정
     * @see UpdateUserUseCase#updateMyProfile(Long, UserUpdateCommand)
     */
    @Override
    @Transactional
    public UserProfileResult updateMyProfile(Long userId, UserUpdateCommand command) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorMessage.NOT_FOUND_USER,
                        String.format("사용자(ID: %d)를 찾을 수 없습니다.", userId))
                );

        // 도메인 모델의 비즈니스 로직 호출 후 저장
        User updatedUser = user.updateProfile(
                command.nickname(),
                command.statusMessage()
        );

        userRepositoryPort.save(updatedUser);
        return UserProfileResult.from(updatedUser);
    }
}