package com.serverbe.adapter.in.web.dto.task;

import com.serverbe.domain.model.task.AiTask;
import com.serverbe.domain.model.task.vo.TaskStatus;

public record TaskStatusResponse(
        String taskId,
        TaskStatus status,
        Long resultArtId,
        String errorMessage
) {
    public static TaskStatusResponse from(AiTask ai) {
        return new TaskStatusResponse(
                ai.id(),
                ai.status(),
                ai.resultArtId(), // 추가된 필드
                ai.errorMessage()
        );
    }
}