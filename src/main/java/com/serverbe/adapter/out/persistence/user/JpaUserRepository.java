package com.serverbe.adapter.out.persistence.user;

import com.serverbe.domain.model.user.vo.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaUserRepository extends JpaRepository<UserEntity, Long> {
    // OAuth 식별자와 제공자로 사용자 찾기
    Optional<UserEntity> findByOauthIdAndProvider(String oauthId, OAuthProvider provider);
    
    // 이메일 중복 확인
    boolean existsByEmail(String email);
}