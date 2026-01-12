package com.serverbe.application.service;

import com.serverbe.application.port.in.me.UpdateUserUseCase;
import com.serverbe.application.port.out.dto.me.UserProfileResult;
import com.serverbe.application.port.out.dto.me.UserUpdateCommand;
import com.serverbe.application.port.in.me.GetUserUseCase;
import com.serverbe.application.port.out.jpa.UserRepositoryPort;
import com.serverbe.domain.exception.user.UserErrorCode;
import com.serverbe.domain.exception.user.UserException;
import com.serverbe.domain.model.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @responsibility 사용자 프로필 조회 및 수정을 담당하는 유스케이스 구현 클래스입니다.
 * @implSpec {@link GetUserUseCase}, {@link UpdateUserUseCase} 인터페이스를 구현합니다.
 */
@Service
@RequiredArgsConstructor
public class UserService implements GetUserUseCase, UpdateUserUseCase {

    private final UserRepositoryPort userRepositoryPort;

    /**
     * @param userId 조회할 사용자의 식별자
     * @return 공개 프로필 정보 (이메일, 닉네임, 상태 메시지 등)
     * @requirement UC-USER-01: 사용자 정보 조회
     * @responsibility 특정 사용자의 프로필 정보를 조회하여 외부 응답용 DTO로 변환합니다.
     * @implSpec {@link UserRepositoryPort#findById(Long)} 호출 시, 어댑터 레벨에서 암호화 버전 마이그레이션이 투명하게 수행됩니다.
     * @implNote 조회된 도메인 엔티티를 {@link UserProfileResult}로 매핑하여 반환함으로써 캡슐화를 유지합니다.
     */
    @Override
    @Transactional
    public UserProfileResult getMyProfile(Long userId) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new UserException(
                        UserErrorCode.NOT_FOUND_USER,
                        String.format("사용자(ID: %d)를 찾을 수 없습니다.", userId))
                );

        return UserProfileResult.from(user);
    }

    /**
     * @param userId  수정할 사용자의 식별자
     * @param command 수정할 필드 정보를 담은 DTO
     * @return 수정이 완료된 프로필 정보
     * @requirement UC-USER-02: 사용자 정보 수정
     * @responsibility 사용자의 닉네임과 상태 메시지를 업데이트하고 변경 사항을 영속화합니다.
     * @implSpec 1. {@link User} 도메인 모델의 내부 비즈니스 로직({@code updateProfile})을 호출하여 상태를 변경합니다.<br>
     * 2. 변경된 도메인 모델을 포트를 통해 저장소에 반영합니다.
     * @implNote 정보 수정 후 최신화된 프로필 정보를 즉시 반환하여 클라이언트의 데이터 동기화를 돕습니다.
     */
    @Override
    @Transactional
    public UserProfileResult updateMyProfile(Long userId, UserUpdateCommand command) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new UserException(
                        UserErrorCode.NOT_FOUND_USER,
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