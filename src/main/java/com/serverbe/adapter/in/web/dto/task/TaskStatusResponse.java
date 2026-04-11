package com.serverbe.adapter.in.web.dto.task;

import com.serverbe.adapter.out.persistence.task.AiTaskEntity;
import com.serverbe.domain.model.art.task.TaskStatus;

/**
 * 프론트엔드 폴링 요청에 대한 상태 응답 DTO
 */
public record TaskStatusResponse(
        String taskId,
        TaskStatus status,
        String outputUrl,     // 완료(COMPLETED) 시 결과물을 확인할 수 있는 경로
        String errorMessage   // 실패(FAILED) 시 에러 원인 (디버깅/UX 용도)
) {
    public static TaskStatusResponse from(AiTaskEntity entity) {
        return new TaskStatusResponse(
                entity.getId(),
                entity.getStatus(),
                entity.getOutputS3Uri(), // 임시로 S3 URI 반환 (추후 CloudFront URL 등으로 매핑 가능)
                entity.getErrorMessage()
        );
    }
}