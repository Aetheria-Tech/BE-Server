package com.serverbe.infrastructure.config.event;

import com.serverbe.adapter.in.web.dto.sqs.SageMakerNotificationDto;
import com.serverbe.application.port.in.task.UpdateTaskStatusUseCase;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiNotificationSqsListener {

    // 이제 SQS 리스너는 SSE 서비스나 DB 레포지토리를 직접 알 필요가 없습니다!
    // 오직 하나의 유스케이스(인바운드 포트)만 의존합니다.
    private final UpdateTaskStatusUseCase updateTaskStatusUseCase;
    private static final String QUEUE_NAME_PROPERTY = "${aws.sqs.ai-notification-queue-name}";

    /**
     * @responsibility SQS 큐에서 SageMaker 완료/실패 알림을 수신하여 UseCase로 전달합니다.
     */
    @SqsListener(QUEUE_NAME_PROPERTY)
    public void receiveAiTaskNotification(SageMakerNotificationDto message) {
        log.info("[SQS Listener] SageMaker 이벤트 수신: {}", message.invocationStatus());

        try {
            if ("Completed".equalsIgnoreCase(message.invocationStatus())) {
                String outputS3Uri = message.responseParameters().outputLocation();
                String taskId = extractTaskIdFromUri(outputS3Uri);

                // UseCase 호출 (내부적으로 DB 업데이트 + SSE 발송을 모두 수행함)
                updateTaskStatusUseCase.completeTask(taskId, outputS3Uri);

            } else {
                // 실패 처리
                String failedS3Uri = message.responseParameters().outputLocation();
                String taskId = extractTaskIdFromUri(failedS3Uri);
                String reason = message.failureReason();

                updateTaskStatusUseCase.failTask(taskId, reason);
            }
        } catch (Exception e) {
            log.error("[SQS Listener] SQS 메시지 처리 중 오류 발생", e);
            throw e; // 예외를 던져야 SQS 메시지가 보존/재시도(DLQ) 됩니다.
        }
    }

    /**
     * S3 URI에서 Task ID (UUID) 부분만 파싱하는 헬퍼 메서드
     */
    private String extractTaskIdFromUri(String s3Uri) {
        if (s3Uri == null || !s3Uri.contains("/")) return "UNKNOWN_TASK";
        String fileName = s3Uri.substring(s3Uri.lastIndexOf("/") + 1);
        return fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf(".")) : fileName;
    }
}