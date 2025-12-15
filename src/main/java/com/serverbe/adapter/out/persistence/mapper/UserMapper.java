package com.serverbe.adapter.out.persistence.mapper;

import com.serverbe.adapter.out.persistence.user.UserEntity;
import com.serverbe.domain.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    // 도메인 모델 -> 엔티티 (저장 시 사용)
    public UserEntity toEntity(User user) {
        return UserEntity.builder()
                .oauthId(user.oauthId())
                .provider(user.provider())
                .email(user.email())
                .nickname(user.nickname())
                .role(user.role())
                .oauthRefreshToken(user.oauthRefreshToken())
                .build();
    }

    // 엔티티 -> 도메인 모델 (조회 시 사용)
    public User toDomain(UserEntity entity) {
        return new User(
                entity.getId(),
                entity.getOauthId(),
                entity.getProvider(),
                entity.getEmail(),
                entity.getNickname(),
                entity.getRole(),
                entity.getOauthRefreshToken()
        );
    }
}