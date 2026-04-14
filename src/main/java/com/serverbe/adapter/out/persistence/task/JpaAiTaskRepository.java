package com.serverbe.adapter.out.persistence.task;

import com.serverbe.domain.model.task.vo.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    /**
     * 특정 상태에 머물러 있으면서 지정된 시간 이상 업데이트되지 않은 좀비 Task를 조회합니다.
     */
    @Query("SELECT t FROM AiTaskEntity t WHERE t.status = :status AND t.updatedAt < :threshold")
    List<AiTaskEntity> findZombieTasks(
            @Param("status") TaskStatus status,
            @Param("threshold") LocalDateTime threshold
    );
}