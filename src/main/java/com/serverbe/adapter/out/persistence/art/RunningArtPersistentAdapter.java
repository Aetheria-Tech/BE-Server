package com.serverbe.adapter.out.persistence.art;

import com.serverbe.adapter.out.persistence.mapper.RunningArtMapper;
import com.serverbe.adapter.out.persistence.user.JpaUserRepository;
import com.serverbe.adapter.out.persistence.user.UserEntity;
import com.serverbe.application.port.out.dto.art.RunningArtUpdateDto;
import com.serverbe.application.port.out.jpa.RunningArtRepositoryPort;
import com.serverbe.domain.model.art.RunningArt;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RunningArtPersistentAdapter implements RunningArtRepositoryPort {

    private final JpaRunningArtRepository jpaRepository;
    private final JpaUserRepository jpaUserRepository;
    private final RunningArtMapper mapper;

    @Override
    public RunningArt save(RunningArt runningArt) {
        // 1. 도메인의 userId를 이용해 UserEntity 참조를 가져옴
        UserEntity userEntity = jpaUserRepository.findById(runningArt.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorMessage.NOT_FOUND_USER, "사용자를 조회할 수 없습니다."));

        // 2. Mapper를 통해 Domain -> Entity 변환 (UserEntity 주입)
        RunningArtEntity entity = mapper.toEntity(runningArt, userEntity);

        // 3. DB 저장 및 결과를 다시 도메인으로 변환하여 반환
        RunningArtEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<RunningArt> findById(Long id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<RunningArt> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void updateMetadata(Long userId, Long runningArtId, RunningArtUpdateDto dto) {
        RunningArtEntity entity = jpaRepository.findById(runningArtId)
                .orElseThrow(() -> new BusinessException(ErrorMessage.NOT_FOUND_RUNNING_ART, "런닝아트를 조회할 수 없습니다"));

        if (!entity.getUser().getId().equals(userId))
            throw new BusinessException(ErrorMessage.USER_IS_NOT_OWNER_OF_RUNNING_ART, "사용자는 런닝아트의 주인이 아닙니다");

        entity.updateMetadata(dto.title(), dto.content());
    }

    @Override
    public void deleteById(Long userId, Long runningArtId) {
        RunningArtEntity entity = jpaRepository.findById(runningArtId)
                .orElseThrow(() -> new BusinessException(ErrorMessage.NOT_FOUND_RUNNING_ART, "런닝아트를 조회할 수 없습니다"));

        if (!entity.getUser().getId().equals(userId))
            throw new BusinessException(ErrorMessage.USER_IS_NOT_OWNER_OF_RUNNING_ART, "사용자는 런닝아트의 주인이 아닙니다");


        jpaRepository.deleteById(runningArtId);
    }

    @Override
    public void deleteByUserId(Long userId) {
        jpaRepository.deleteByUserId(userId);
    }

    @Override
    public List<RunningArt> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}