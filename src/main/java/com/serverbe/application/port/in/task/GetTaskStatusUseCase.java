package com.serverbe.application.port.in.task;

import com.serverbe.adapter.in.web.dto.task.TaskStatusResponse;

public interface GetTaskStatusUseCase {
    TaskStatusResponse getTaskStatus(String taskId, Long userId);
}