package com.serverbe.adapter.out.persistence.task;

import com.serverbe.domain.model.task.vo.TaskStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 비동기 AI 추론 상태를 관리하는 Task 엔티티
 */
@Entity
@Table(name = "ai_generation_tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class AiTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "VARCHAR(36)")
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status;

    @Column(name = "input_s3_uri", length = 500)
    private String inputS3Uri;

    @Column(name = "output_s3_uri", length = 500)
    private String outputS3Uri;

    // FAILED 상태일 때 클라이언트나 백엔드 로깅용으로 원인을 파악하기 위한 컬럼
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "result_art_id")
    private Long resultArtId; // AI 생성이 완료되어 저장된 RunningArt의 ID

    @Builder
    public AiTaskEntity(Long userId, String inputS3Uri) {
        this.userId = userId;
        this.status = TaskStatus.PENDING; // 초기 상태는 항상 PENDING
        this.inputS3Uri = inputS3Uri;
    }

    public void updateStatus(TaskStatus status) {
        this.status = status;
    }

    public void markAsCompleted(Long runningArtId) {
        this.status = TaskStatus.COMPLETED;
        this.resultArtId = runningArtId;
    }

    public void markAsFailed(String errorMessage) {
        this.status = TaskStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    // AiTaskEntity 내부에 추가
    public void markAsProcessing(String inputS3Uri, String outputS3Uri) {
        this.status = TaskStatus.PROCESSING;
        this.inputS3Uri = inputS3Uri;
        this.outputS3Uri = outputS3Uri;
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}