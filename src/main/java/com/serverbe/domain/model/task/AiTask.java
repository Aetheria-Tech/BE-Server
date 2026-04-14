package com.serverbe.domain.model.task;

import com.serverbe.domain.model.task.vo.TaskStatus;

import java.time.LocalDateTime;

/**
 * @responsibility AI 추론 작업의 상태와 데이터를 관리하는 순수 도메인 모델
 * @implNote 불변성(Immutability)을 보장하기 위해 Java Record로 구현되었으며, 
 * 상태 변경 시 새로운 인스턴스를 반환합니다.
 */
public record AiTask(
        String id,
        Long userId,
        TaskStatus status,
        String inputS3Uri,
        String outputS3Uri,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long resultArtId
) {

    // 💡 비즈니스 로직: 상태 변경 시 새로운 Record 인스턴스를 생성하여 반환합니다.

    public AiTask markAsProcessing(String inputS3Uri, String outputS3Uri) {
        return new AiTask(
                this.id, this.userId, TaskStatus.PROCESSING,
                inputS3Uri, outputS3Uri, this.errorMessage,
                this.createdAt, LocalDateTime.now(), this.resultArtId
        );
    }

    public AiTask markAsCompleted(Long runningArtId) {
        return new AiTask(
                this.id, this.userId, TaskStatus.COMPLETED,
                this.inputS3Uri, this.outputS3Uri, this.errorMessage,
                this.createdAt, LocalDateTime.now(), runningArtId
        );
    }

    public AiTask markAsFailed(String errorMessage) {
        return new AiTask(
                this.id, this.userId, TaskStatus.FAILED,
                this.inputS3Uri, this.outputS3Uri, errorMessage,
                this.createdAt, LocalDateTime.now(), this.resultArtId
        );
    }
}