package com.serverbe.adapter.in.web.dto.task;

import com.serverbe.adapter.out.persistence.task.AiTaskEntity;
import com.serverbe.domain.model.art.task.TaskStatus;

public record TaskStatusResponse(
        String taskId,
        TaskStatus status,
        Long resultArtId,     // ✨ 완료 시 생성된 실제 RunningArt의 DB ID를 넘겨줍니다.
        String errorMessage
) {
    public static TaskStatusResponse from(AiTaskEntity entity) {
        return new TaskStatusResponse(
                entity.getId(),
                entity.getStatus(),
                entity.getResultArtId(), // 추가된 필드
                entity.getErrorMessage()
        );
    }
}