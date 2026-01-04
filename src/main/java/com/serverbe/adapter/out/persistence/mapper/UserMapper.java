package com.serverbe.adapter.out.persistence.mapper;

import com.serverbe.adapter.out.persistence.user.UserEntity;
import com.serverbe.domain.model.user.User;
import com.serverbe.infrastructure.crypto.EncryptionContext;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    // Domain -> Entity
    public UserEntity toEntity(User user) {
        return UserEntity.builder()
                .id(user.id())
                .oauthId(user.oauthId())
                .provider(user.provider())
                .email(user.email())
                .nickname(user.nickname())
                .statusMessage(user.statusMessage())
                .role(user.role())
                .oauthRefreshToken(user.oauthRefreshToken())
                .build();
    }

    // Entity -> Domain
    public User toDomain(UserEntity entity) {
        if (entity == null) {
            return null; // 또는 예외 처리
        }

        boolean migrationRequired = EncryptionContext.isMigrationRequired();
        EncryptionContext.clear();

        return new User(
                entity.getId(),
                entity.getOauthId(),
                entity.getProvider(),
                entity.getEmail(),
                entity.getNickname(),
                entity.getRole(),
                entity.getStatusMessage(),
                entity.getOauthRefreshToken(),
                migrationRequired
        );
    }
}