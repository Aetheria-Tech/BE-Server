package com.serverbe.adapter.out.persistence.task;

import com.serverbe.domain.model.art.task.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface JpaAiTaskRepository extends JpaRepository<AiTaskEntity, String> {

    /**
     * 보안을 위해 클라이언트 폴링 시 본인의 Task가 맞는지 ID와 UserID를 동시에 검증합니다.
     */
    Optional<AiTaskEntity> findByIdAndUserId(String id, Long userId);

    /**
     * 특정 상태(예: PROCESSING)에 있는 모든 Task 목록을 조회합니다.
     * 스케줄러가 결과 회수를 위해 진행 중인 작업들을 찾을 때 사용합니다.
     */
    List<AiTaskEntity> findAllByStatus(TaskStatus status);
}