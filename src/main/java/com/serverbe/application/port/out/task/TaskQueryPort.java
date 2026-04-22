package com.serverbe.application.port.out.task;

import com.serverbe.domain.model.task.AiTask;
import com.serverbe.domain.model.task.vo.TaskStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TaskQueryPort {
    Optional<AiTask> findById(String taskId);
    List<AiTask> findZombieTasks(LocalDateTime threshold);
    List<AiTask> findAllByStatus(TaskStatus status);
}