package com.serverbe.domain.model.task;

import com.serverbe.domain.exception.ai.AiErrorCode;
import com.serverbe.domain.exception.ai.AiException;
import com.serverbe.domain.model.art.vo.Proficiency;
import com.serverbe.domain.model.task.vo.TaskStatus;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * @responsibility AI 추론 작업의 상태와 데이터를 관리하는 순수 도메인 모델
 * @implNote 불변성(Immutability)을 보장하기 위해 Java Record로 구현되었으며,
 * 상태 변경 시 새로운 인스턴스를 반환합니다.
 */
public record AiTask(
        String id,
        Long userId,
        String shape,
        Proficiency proficiency,
        TaskStatus status,
        String inputS3Uri,
        String outputS3Uri,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long resultArtId
) {

    /**
     * 정적 팩토리 메서드: 최초 PENDING 상태의 인스턴스를 생성합니다.
     *
     * @implNote ID는 null로 세팅되며, 영속성 어댑터(JPA)에서 save() 될 때 자동 할당됩니다.
     */
    public static AiTask createPending(Long userId, String shape, Proficiency proficiency) {
        return new AiTask(
                null, // 신규 생성이므로 id는 null (Adapter의 save 로직에서 null 체크 후 insert 수행)
                userId,
                shape,
                proficiency,
                TaskStatus.PENDING,
                null,
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null
        );
    }

    /**
     * 비즈니스 로직: 요청자가 이 Task의 소유자인지 검증합니다. (Tell, Don't Ask 원칙)
     */
    public void validateOwner(Long requestUserId) {
        if (!Objects.equals(this.userId, requestUserId)) {
            throw new AiException(AiErrorCode.USER_IS_NOT_OWNER_OF_TASK);
        }
    }

    // 비즈니스 로직: 상태 변경 시 새로운 Record 인스턴스를 생성하여 반환합니다.

    public AiTask markAsProcessing(String inputS3Uri, String outputS3Uri) {
        return new AiTask(
                this.id,
                this.userId,
                this.shape,
                this.proficiency,
                TaskStatus.PROCESSING,
                inputS3Uri,
                outputS3Uri,
                this.errorMessage,
                this.createdAt,
                LocalDateTime.now(),
                this.resultArtId
        );
    }

    public AiTask markAsCompleted(Long runningArtId) {
        return new AiTask(
                this.id,
                this.userId,
                this.shape,
                this.proficiency,
                TaskStatus.COMPLETED,
                this.inputS3Uri,
                this.outputS3Uri,
                this.errorMessage,
                this.createdAt,
                LocalDateTime.now(),
                runningArtId
        );
    }

    public AiTask markAsFailed(String errorMessage) {
        String truncatedMessage = (errorMessage != null && errorMessage.length() > 1000)
                ? errorMessage.substring(0, 997) + "..."
                : errorMessage;

        return new AiTask(
                this.id,
                this.userId,
                this.shape,
                this.proficiency,
                TaskStatus.FAILED,
                this.inputS3Uri,
                this.outputS3Uri,
                truncatedMessage,
                this.createdAt,
                LocalDateTime.now(),
                this.resultArtId
        );
    }
}