package com.serverbe.adapter.out.persistence.mapper;

import com.serverbe.adapter.out.persistence.art.RunningArtEntity;
import com.serverbe.adapter.out.persistence.user.UserEntity;
import com.serverbe.domain.model.art.RunningArt;
import org.springframework.stereotype.Component;

/**
 * @responsibility 데이터베이스 엔티티와 도메인 모델 간의 변환을 담당합니다.
 * @implSpec {@link RunningArtEntity}와 <b>record</b>로 구현된 {@link RunningArt} 사이의 매핑 로직을 관리합니다.
 */
@Component
public class RunningArtMapper {

    /**
     * @responsibility 영속성 엔티티를 도메인 모델로 변환합니다.
     * @param entity 변환할 {@link RunningArtEntity}
     * @return 변환된 {@link RunningArt} 레코드, 엔티티가 null일 경우 null 반환
     */
    public RunningArt toDomain(RunningArtEntity entity) {
        if (entity == null) return null;

        return RunningArt.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .shape(entity.getShape())
                .proficiency(entity.getProficiency())
                .gpx(entity.getGpx())
                .userId(entity.getUser() != null ? entity.getUser().getId() : null)
                .startLat(entity.getStartLat())
                .startLon(entity.getStartLon())
                .build();
    }

    /**
     * @responsibility 도메인 모델을 영속성 엔티티로 변환합니다.
     * @implNote 레코드의 접근자 메서드(Accessors)를 사용하여 데이터를 추출하며, 연관된 {@link UserEntity}를 주입합니다.
     * @param domain 변환할 {@link RunningArt} 레코드
     * @param user 연관 관계 설정을 위한 {@link UserEntity}
     * @return 구성된 {@link RunningArtEntity}, 도메인 객체가 null일 경우 null 반환
     */
    public RunningArtEntity toEntity(RunningArt domain, UserEntity user) {
        if (domain == null) return null;

        return RunningArtEntity.builder()
                .title(domain.title())
                .content(domain.content())
                .shape(domain.shape())
                .proficiency(domain.proficiency())
                .gpx(domain.gpx())
                .user(user)
                .build();
    }
}