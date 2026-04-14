package com.serverbe.application.port.out.task;

import com.serverbe.domain.model.task.AiTask;
import java.time.LocalDateTime;
import java.util.List;

public interface TaskQueryPort {
    List<AiTask> findZombieTasks(LocalDateTime threshold);
}