package com.serverbe.adapter.out.persistence.mapper;

import com.serverbe.adapter.out.persistence.art.RunningArtEntity;
import com.serverbe.adapter.out.persistence.user.UserEntity;
import com.serverbe.domain.model.art.RunningArt;
import org.springframework.stereotype.Component;

@Component
public class RunningArtMapper {

    // Entity -> Domain
    public RunningArt toDomain(RunningArtEntity entity) {
        if (entity == null) return null;

        return new RunningArt(
                entity.getId(),
                entity.getTitle(),
                entity.getContent(),
                entity.getShape(),
                entity.getProficiency(),
                entity.getGpx(),
                entity.getUser() != null ? entity.getUser().getId() : null
        );
    }

    // Domain -> Entity
    public RunningArtEntity toEntity(RunningArt domain, UserEntity user) {
        if (domain == null) return null;

        return RunningArtEntity.builder()
                .title(domain.getTitle())
                .content(domain.getContent())
                .shape(domain.getShape())
                .proficiency(domain.getProficiency())
                .gpx(domain.getGpx())
                .user(user) // 매핑 시 연관된 UserEntity 주입
                .build();
    }
}