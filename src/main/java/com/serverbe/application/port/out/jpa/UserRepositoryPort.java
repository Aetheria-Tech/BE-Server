package com.serverbe.application.port.out.jpa;

import com.serverbe.domain.model.user.User;
import com.serverbe.domain.model.user.vo.OAuthProvider;

import java.util.Optional;

/**
 * @responsibility 유저(User) 도메인 엔티티의 영속성 관리를 위한 아웃바운드 포트 인터페이스입니다.
 * 외부 저장소(DB)와의 상호작용을 추상화하여 도메인 비즈니스 로직이 특정 기술 프레임워크에 종속되지 않도록 합니다.
 */
public interface UserRepositoryPort {

    /**
     * @responsibility OAuth 서비스 제공자의 식별 정보와 제공자 유형을 기반으로 등록된 사용자를 조회합니다.
     * @param oauthId OAuth 서비스에서 발급한 사용자의 고유 식별자
     * @param provider OAuth 서비스를 제공하는 주체 (예: KAKAO, GOOGLE) {@link OAuthProvider}
     * @return 조회된 사용자 정보를 포함하는 {@link Optional<User>}
     */
    Optional<User> findByOauthId(String oauthId, OAuthProvider provider);

    /**
     * @responsibility 시스템 내부의 고유 식별자(PK)를 통해 특정 사용자를 조회합니다.
     * @param id 조회의 기준이 되는 유저의 고유 ID
     * @return 식별자에 해당하는 사용자 정보를 담은 {@link Optional<User>}
     */
    Optional<User> findById(Long id);

    /**
     * @responsibility 새로운 사용자 정보를 영구 저장소에 등록하거나, 기존 사용자 정보를 최신 상태로 갱신합니다.
     * @param user 저장 또는 수정하고자 하는 {@link User} 도메인 모델
     * @return 저장 프로세스가 완료되어 식별자가 할당되거나 상태가 반영된 {@link User} 도메인 모델
     */
    User save(User user);

    /**
     * @responsibility 시스템 내에 특정 이메일 주소를 사용하는 사용자가 이미 존재하는지 여부를 확인합니다.
     * @param email 가입 여부를 확인할 이메일 주소
     * @return 해당 이메일로 가입된 사용자가 존재하면 true, 존재하지 않으면 false
     */
    boolean existsByEmail(String email);

    /**
     * @responsibility 시스템 내부 고유 식별자를 기준으로 사용자 데이터를 영구 저장소에서 삭제합니다.
     * @param id 삭제 대상 사용자의 고유 식별자
     */
    void deleteById(Long id);
}