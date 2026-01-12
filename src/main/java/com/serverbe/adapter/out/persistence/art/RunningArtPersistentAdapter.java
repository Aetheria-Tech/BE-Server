package com.serverbe.adapter.out.persistence.art;

import com.serverbe.adapter.out.persistence.mapper.RunningArtMapper;
import com.serverbe.adapter.out.persistence.user.JpaUserRepository;
import com.serverbe.adapter.out.persistence.user.UserEntity;
import com.serverbe.application.port.in.dto.art.RunningArtUpdateCommand;
import com.serverbe.application.port.out.jpa.RunningArtRepositoryPort;
import com.serverbe.domain.exception.art.ArtErrorCode;
import com.serverbe.domain.exception.art.ArtException;
import com.serverbe.domain.model.art.RunningArt;
import com.serverbe.domain.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * @author Duskafka
 * @responsibility 런닝아트 도메인 모델과 데이터베이스 엔티티 간의 매핑 및 데이터 영속화를 담당하는 아웃바운드 어댑터입니다.
 * @implSpec Spring Data JPA를 사용하여 데이터베이스와 상호작용하며, 도메인 레이어의 인터페이스인 {@link RunningArtRepositoryPort}를 구현합니다.
 * @see RunningArtRepositoryPort
 */
@Repository
@RequiredArgsConstructor
public class RunningArtPersistentAdapter implements RunningArtRepositoryPort {

    private final JpaRunningArtRepository jpaRepository;
    private final JpaUserRepository jpaUserRepository;
    private final RunningArtMapper mapper;

    /**
     * @param runningArt 저장할 런닝아트 도메인 모델
     * @return 저장된 정보를 포함하는 {@link RunningArt} 도메인 모델
     * @responsibility 새로운 런닝아트 정보를 저장하거나 기존 정보를 업데이트합니다.
     * @implSpec {@link JpaUserRepository#getReferenceById(Object)}를 사용하여 연관된 사용자 엔티티의 프록시를 가져온 후, 매퍼를 통해 엔티티로 변환하여 저장합니다.
     * @implNote 실제 DB 조회를 피하기 위해 getReferenceById를 사용하므로, 해당 ID의 사용자가 존재하지 않을 경우 저장 시점에 예외가 발생할 수 있습니다.
     */
    @Override
    public RunningArt save(RunningArt runningArt) {
        // 1. 도메인의 userId를 이용해 UserEntity 참조를 가져옴
        UserEntity userEntity = jpaUserRepository.getReferenceById(runningArt.userId());

        // 2. Mapper를 통해 Domain -> Entity 변환 (UserEntity 주입)
        RunningArtEntity entity = mapper.toEntity(runningArt, userEntity);

        // 3. DB 저장 및 결과를 다시 도메인으로 변환하여 반환
        RunningArtEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    /**
     * @param id 조회할 런닝아트의 고유 ID
     * @return 조회 결과를 포함하는 {@link Optional<RunningArt>}
     * @responsibility 특정 식별자를 가진 런닝아트를 조회합니다.
     * @implSpec {@link JpaRunningArtRepository#findById(Object)}를 호출하고 결과를 도메인 모델로 변환합니다.
     */
    @Override
    public Optional<RunningArt> findById(Long id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    /**
     * @param pageable 페이징 정보
     * @return 조회된 {@link RunningArt} 도메인 리스트
     * @responsibility 저장된 모든 런닝아트 목록을 조회합니다.
     * @implSpec {@link JpaRunningArtRepository#findAll()}을 통해 전체 엔티티를 조회한 후 Stream API를 사용하여 도메인 목록으로 변환합니다.
     * @implNote 데이터 양이 많을 경우 성능 저하의 원인이 될 수 있으므로 페이징 처리를 권장합니다.
     */
    @Override
    public Page<RunningArt> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(mapper::toDomain);
    }

    /**
     * @param runningArtId 수정할 런닝아트 ID
     * @param dto          수정할 정보를 담은 {@link RunningArtUpdateCommand} DTO
     * @responsibility 런닝아트의 메타데이터(제목, 내용)를 수정합니다.
     * @implSpec 영속성 컨텍스트 내의 엔티티를 조회한 후 엔티티 내부의 수정 메서드를 호출하여 Dirty Checking에 의해 업데이트가 수행되도록 합니다.
     * @implNote 해당 ID의 데이터가 없을 경우 {@link BusinessException}을 발생시킵니다.
     */
    @Override
    public void updateMetadata(Long runningArtId, RunningArtUpdateCommand dto) {
        RunningArtEntity entity = jpaRepository.findById(runningArtId)
                .orElseThrow(() -> new ArtException(ArtErrorCode.NOT_FOUND_RUNNING_ART, "런닝아트를 조회할 수 없습니다"));

        entity.updateMetadata(dto.title(), dto.content());
    }

    /**
     * @param runningArtId 삭제할 런닝아트 ID
     * @responsibility 고유 ID를 기준으로 런닝아트를 삭제합니다.
     * @implSpec {@link JpaRunningArtRepository#deleteById(Object)}를 호출하여 삭제 작업을 수행합니다.
     */
    @Override
    public void deleteById(Long runningArtId) {
        jpaRepository.deleteById(runningArtId);
    }

    /**
     * @param userId 삭제할 대상 사용자의 고유 ID
     * @responsibility 특정 사용자가 생성한 모든 런닝아트를 삭제합니다.
     * @implSpec 사용자 ID를 기반으로 대량 삭제 요청을 JPA 리포지토리에 전달합니다.
     * @implNote 대량 삭제 시 영속성 컨텍스트의 상태와 DB 상태 간의 불일치에 주의해야 하며, 필요시 쿼리 메서드에 @Modifying을 사용합니다.
     */
    @Override
    public void deleteByUserId(Long userId) {
        jpaRepository.deleteByUserId(userId);
    }

    /**
     * @param pageable 페이징 정보
     * @param userId   조회할 사용자의 고유 ID
     * @return 해당 사용자의 {@link List<RunningArt>} 목록
     * @responsibility 특정 사용자가 생성한 런닝아트 목록을 조회합니다.
     * @implSpec 엔티티 간의 연관 관계 필드(User)를 기반으로 명명 규칙에 따른 JPA 쿼리 메서드를 사용합니다.
     */
    @Override
    public Page<RunningArt> findByUserId(Long userId, Pageable pageable) {
        return jpaRepository.findByUser_Id(userId, pageable).map(mapper::toDomain);
    }
}