package com.serverbe.application.port.out;

import com.serverbe.domain.model.User;
import com.serverbe.domain.model.vo.OAuthProvider;

import java.util.Optional;

/**
 * 사용자 데이터 영속성을 위한 아웃고잉 포트 인터페이스입니다.
 * 외부 계층의 Persistence Adapter에 의해 구현됩니다.
 */
public interface UserRepositoryPort {

    /**
     * OAuth 식별자와 제공자(카카오, 구글)를 통해 사용자를 조회합니다.
     * 소셜 로그인 시 기존 회원 여부를 판단하기 위해 사용됩니다.
     */
    Optional<User> findByOauthId(String oauthId, OAuthProvider provider);

    /**
     * 사용자 고유 ID(PK)로 사용자를 조회합니다.
     */
    Optional<User> findById(Long id);


    /**
     * 사용자 정보를 저장하거나 업데이트합니다.
     * @param user 저장할 도메인 모델
     * @return 저장된 도메인 모델
     */
    User save(User user);
    
    /**
     * 이메일 중복 체크 등을 위해 사용될 수 있습니다.
     */
    boolean existsByEmail(String email);

    /**
     * 사용자 고유 ID(PK)로 사용자를 삭제.
     * */
    void deleteById(Long id);
}