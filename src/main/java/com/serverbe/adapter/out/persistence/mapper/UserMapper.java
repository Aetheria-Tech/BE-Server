package com.serverbe.adapter.out.persistence.mapper;

import com.serverbe.adapter.out.persistence.user.UserEntity;
import com.serverbe.domain.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserEntity toEntity(User user) {
        return UserEntity.builder()
                .id(user.id())
                .oauthId(user.oauthId())
                .provider(user.provider())
                .email(user.email())
                .nickname(user.nickname())
                .statusMessage(user.statusMessage()) // 추가!
                .role(user.role())
                .oauthRefreshToken(user.oauthRefreshToken())
                .build();
    }

    public User toDomain(UserEntity entity) {
        return new User(
                entity.getId(),
                entity.getOauthId(),
                entity.getProvider(),
                entity.getEmail(),
                entity.getNickname(),
                entity.getRole(),
                entity.getStatusMessage(),
                entity.getOauthRefreshToken()
        );
    }
}