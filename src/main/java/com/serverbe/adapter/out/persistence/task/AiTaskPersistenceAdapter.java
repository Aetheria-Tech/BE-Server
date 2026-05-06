package com.serverbe.adapter.out.persistence.task;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.serverbe.adapter.out.persistence.mapper.AiTaskMapper;
import com.serverbe.application.port.out.task.TaskQueryPort;
import com.serverbe.application.port.out.task.TaskUpdatePort;
import com.serverbe.domain.model.task.AiTask;
import com.serverbe.domain.model.task.vo.TaskStatus;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.serverbe.adapter.out.persistence.task.QAiTaskEntity.aiTaskEntity;

@Component
@RequiredArgsConstructor
public class AiTaskPersistenceAdapter implements TaskQueryPort, TaskUpdatePort {

    private final JpaAiTaskRepository jpaRepository;
    private final AiTaskMapper aiTaskMapper;
    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<AiTask> findById(String taskId) {
        return jpaRepository.findById(taskId).map(aiTaskMapper::toDomain);
    }

    @Override
    public List<AiTask> findZombieTasks(List<TaskStatus> statuses, LocalDateTime threshold) {
        List<AiTaskEntity> entities = queryFactory
                .selectFrom(aiTaskEntity)
                .where(
                        aiTaskEntity.status.in(statuses),
                        aiTaskEntity.updatedAt.lt(threshold)
                )
                .fetch();

        return entities.stream()
                .map(aiTaskMapper::toDomain)
                .toList();
    }

    @Override
    public List<AiTask> findAllByStatus(TaskStatus status) {
        // DB에서 엔티티 리스트를 가져와서, 이전에 잘 만들어두신 Mapper를 통해 도메인 리스트로 변환!
        List<AiTaskEntity> entities = jpaRepository.findAllByStatus(status);

        return entities.stream()
                .map(aiTaskMapper::toDomain)
                .toList();
    }

    @Override
    public AiTask save(AiTask aiTask) {
        if (aiTask.id() == null) {
            // 신규 생성
            AiTaskEntity newEntity = aiTaskMapper.toEntity(aiTask);
            AiTaskEntity save = jpaRepository.save(newEntity);
            return aiTaskMapper.toDomain(save);
        } else {
            // 기존 데이터 업데이트 (Dirty Checking 발동)
            AiTaskEntity existingEntity = jpaRepository.findById(aiTask.id())
                    .orElseThrow(() -> new EntityNotFoundException("Task not found with ID: " + aiTask.id()));

            aiTaskMapper.updateEntityFromDomain(aiTask, existingEntity);
            return aiTaskMapper.toDomain(jpaRepository.save(existingEntity));
        }
    }

    @Override
    public Optional<AiTask> findByIdForUpdate(String taskId) {
        return jpaRepository.findByIdForUpdate(taskId).map(aiTaskMapper::toDomain);
    }

    @Override
    public boolean existsActiveTaskByUserId(Long userId) {
        return jpaRepository.existsByUserIdAndStatusIn(
                userId,
                List.of(TaskStatus.PENDING, TaskStatus.PROCESSING)
        );
    }
}