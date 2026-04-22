package com.serverbe.adapter.out.persistence.task;

import com.serverbe.adapter.out.persistence.mapper.AiTaskMapper;
import com.serverbe.application.port.out.task.TaskQueryPort;
import com.serverbe.application.port.out.task.TaskUpdatePort;
import com.serverbe.domain.model.task.AiTask;
import com.serverbe.domain.model.task.vo.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AiTaskPersistenceAdapter implements TaskQueryPort, TaskUpdatePort {

    private final JpaAiTaskRepository jpaRepository;
    private final AiTaskMapper aiTaskMapper;

    @Override
    public Optional<AiTask> findById(String taskId) {
        return jpaRepository.findById(taskId).map(aiTaskMapper::toDomain);
    }

    @Override
    public List<AiTask> findZombieTasks(LocalDateTime threshold) {
        List<AiTaskEntity> entities = jpaRepository.findZombieTasks(TaskStatus.PROCESSING, threshold);

        return entities.stream()
                .map(aiTaskMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<AiTask> findAllByStatus(TaskStatus status) {
        // DB에서 엔티티 리스트를 가져와서, 이전에 잘 만들어두신 Mapper를 통해 도메인 리스트로 변환!
        List<AiTaskEntity> entities = jpaRepository.findAllByStatus(status);

        return entities.stream()
                .map(aiTaskMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void save(AiTask domainTask) {
        if (domainTask.id() == null) {
            // 신규 생성
            AiTaskEntity newEntity = aiTaskMapper.toEntity(domainTask);
            jpaRepository.save(newEntity);
        } else {
            // 기존 데이터 업데이트 (Dirty Checking 발동)
            AiTaskEntity existingEntity = jpaRepository.findById(domainTask.id())
                    .orElseThrow(() -> new IllegalArgumentException("Task를 찾을 수 없습니다."));

            aiTaskMapper.updateEntityFromDomain(domainTask, existingEntity);
        }
    }
}