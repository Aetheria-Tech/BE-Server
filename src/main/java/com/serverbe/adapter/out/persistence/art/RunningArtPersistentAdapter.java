package com.serverbe.adapter.out.persistence.art;

import com.serverbe.adapter.out.persistence.mapper.RunningArtMapper;
import com.serverbe.adapter.out.persistence.user.JpaUserRepository;
import com.serverbe.adapter.out.persistence.user.UserEntity;
import com.serverbe.application.port.out.dto.art.RunningArtUpdateDto;
import com.serverbe.application.port.out.jpa.RunningArtRepositoryPort;
import com.serverbe.domain.model.art.RunningArt;
import jakarta.persistence.EntityNotFoundException;
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
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다. ID: " + runningArt.getUserId()));

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
    public void updateMetadata(Long id, RunningArtUpdateDto dto) {
        RunningArtEntity entity = jpaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("해당 작품을 찾을 수 없습니다. ID: " + id));

        entity.updateMetadata(dto.title(), dto.content());
    }

    @Override
    public void deleteById(Long id) {
        if (!jpaRepository.existsById(id)) {
            throw new EntityNotFoundException("삭제할 대상이 존재하지 않습니다. ID: " + id);
        }
        jpaRepository.deleteById(id);
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