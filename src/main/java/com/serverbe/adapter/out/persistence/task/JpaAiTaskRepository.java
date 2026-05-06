package com.serverbe.adapter.out.persistence.task;

import com.serverbe.domain.model.task.vo.TaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
     * 동시성 제어를 위한 비관적 락(쓰기 락) 조회
     * 다른 트랜잭션이 이 Row에 접근하지 못하고 대기하게 만듭니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM AiTaskEntity t WHERE t.id = :id")
    Optional<AiTaskEntity> findByIdForUpdate(@Param("id") String id);

    boolean existsByUserIdAndStatusIn(Long userId, List<TaskStatus> statuses);
}