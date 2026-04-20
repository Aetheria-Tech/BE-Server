package com.serverbe.adapter.out.persistence.mapper;

import com.serverbe.adapter.out.persistence.task.AiTaskEntity;
import com.serverbe.domain.model.task.AiTask;
import org.springframework.stereotype.Component;

/**
 * @responsibility 데이터베이스 엔티티와 도메인 모델 간의 변환을 담당합니다.
 * @implSpec {@link AiTaskEntity}와 <b>record</b>로 구현된 {@link AiTask} 사이의 매핑 로직을 관리합니다.
 */
@Component
public class AiTaskMapper {

    /**
     * @responsibility 영속성 엔티티를 순수 도메인 모델(Record)로 변환합니다.
     * @param entity 변환할 {@link AiTaskEntity}
     * @return 변환된 {@link AiTask} 레코드, 엔티티가 null일 경우 null 반환
     */
    public AiTask toDomain(AiTaskEntity entity) {
        if (entity == null) return null;

        // Record는 불변 객체이므로 생성자를 통해 모든 값을 주입하여 반환합니다.
        return new AiTask(
                entity.getId(),
                entity.getUserId(),
                entity.getStatus(),
                entity.getInputS3Uri(),
                entity.getOutputS3Uri(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getResultArtId()
        );
    }

    /**
     * @responsibility 새로운 도메인 모델을 데이터베이스에 처음 저장하기 위한 영속성 엔티티로 변환합니다.
     * @implNote 기존 AiTaskEntity의 빌더 구조에 맞추어 필수 필드(userId, inputS3Uri)만 주입하여 초기화합니다.
     * @param domain 변환할 {@link AiTask} 레코드
     * @return 구성된 {@link AiTaskEntity}, 도메인 객체가 null일 경우 null 반환
     */
    public AiTaskEntity toEntity(AiTask domain) {
        if (domain == null) return null;

        return AiTaskEntity.builder()
                .userId(domain.userId())
                .inputS3Uri(domain.inputS3Uri())
                .build();
    }

    /**
     * @responsibility 비즈니스 로직에 의해 상태가 변경된 도메인 모델의 값을 기존 영속성 엔티티에 병합(Merge)합니다.
     * @implNote JPA의 변경 감지(Dirty Checking)를 활용하기 위해 기존 엔티티 객체의 상태를 업데이트합니다.
     * @param domain 변경된 상태를 가진 {@link AiTask} 레코드
     * @param entity 상태를 덮어씌울 타겟 {@link AiTaskEntity}
     */
    public void updateEntityFromDomain(AiTask domain, AiTaskEntity entity) {
        if (domain == null || entity == null) return;

        // 1. 도메인의 상태를 엔티티에 동기화 (가장 중요)
        entity.updateStatus(domain.status());

        // 2. 도메인의 현재 상태에 맞춰서 필요한 데이터만 갱신 (if문 중첩으로 인한 상태 덮어쓰기 방지!)
        switch (domain.status()) {
            case PENDING -> {
            }
            case PROCESSING -> {
                entity.markAsProcessing(domain.inputS3Uri(), domain.outputS3Uri());
            }
            case COMPLETED -> {
                entity.markAsCompleted(domain.resultArtId());
            }
            case FAILED -> {
                entity.markAsFailed(domain.errorMessage());
            }
        }
    }
}