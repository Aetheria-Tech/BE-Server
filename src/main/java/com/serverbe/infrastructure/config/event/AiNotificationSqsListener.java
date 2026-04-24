package com.serverbe.infrastructure.config.event;

import com.serverbe.adapter.in.web.dto.sqs.SageMakerNotificationDto;
import com.serverbe.application.port.in.task.RetrieveAiResultUseCase;
import com.serverbe.application.port.out.notification.TaskNotificationPort;
import com.serverbe.application.port.out.task.TaskQueryPort;
import com.serverbe.application.port.out.task.TaskUpdatePort;
import com.serverbe.domain.exception.ai.AiErrorCode;
import com.serverbe.domain.exception.ai.AiException;
import com.serverbe.domain.model.task.AiTask;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiNotificationSqsListener {

    private final TaskQueryPort taskQueryPort;
    private final TaskUpdatePort taskUpdatePort;
    private final RetrieveAiResultUseCase retrieveAiResultUseCase;
    private final TaskNotificationPort taskNotificationPort;

    private static final String QUEUE_NAME_PROPERTY = "${aws.sqs.ai-notification-queue-name}";

    /**
     * @responsibility SQS 큐에서 SageMaker 완료/실패 알림을 수신하여 핵심 비즈니스 로직으로 전달합니다.
     */
    @SqsListener(QUEUE_NAME_PROPERTY)
    public void receiveAiTaskNotification(SageMakerNotificationDto message) {
        log.info("[SQS Listener] SageMaker 이벤트 수신: {}", message.invocationStatus());

        try {
            if ("Completed".equalsIgnoreCase(message.invocationStatus())) {
                String outputS3Uri = message.responseParameters().outputLocation();
                String taskId = extractTaskIdFromUri(outputS3Uri);

                // 1. DB에서 순수 Task 도메인 모델 조회
                AiTask task = getTaskOrThrow(taskId);

                // 2. 💡 형님이 작성하신 '견고한 로직' 실행 (S3 다운로드 -> RunningArt 엔티티 저장 -> 완료 상태 업데이트)
                retrieveAiResultUseCase.processTaskResult(task);

            } else {
                // 실패 처리
                String failedS3Uri = message.responseParameters().outputLocation();
                String taskId = extractTaskIdFromUri(failedS3Uri);
                String reason = message.failureReason();

                AiTask task = getTaskOrThrow(taskId);
                AiTask failedTask = task.markAsFailed("SageMaker 추론 실패: " + reason);
                taskUpdatePort.save(failedTask);

                // SageMaker 실패 시 프론트엔드에 알림 발송!
                taskNotificationPort.notifyTaskFailed(taskId, reason);

                log.error("[SQS Listener] Task 실패 처리 완료 - TaskID: {}, 사유: {}", taskId, reason);
            }
        } catch (Exception e) {
            log.error("[SQS Listener] SQS 메시지 처리 중 오류 발생", e);
            throw e; // 예외를 던져야 SQS 메시지가 보존/재시도(DLQ) 됩니다.
        }
    }

    /**
     * 중복되는 Task 조회 및 예외 처리를 공통화한 헬퍼 메서드
     */
    private AiTask getTaskOrThrow(String taskId) {
        return taskQueryPort.findById(taskId)
                .orElseThrow(() -> new AiException(AiErrorCode.NOT_FOUND_AITASK, "존재하지 않는 Task입니다. TaskID: " + taskId));
    }

    /**
     * S3 URI에서 Task ID (UUID) 부분만 파싱하는 헬퍼 메서드
     * SageMaker 특유의 다중 확장자({taskId}.json.out) 문제를 방지합니다.
     */
    private String extractTaskIdFromUri(String s3Uri) {
        if (s3Uri == null || !s3Uri.contains("/")) {
            return "UNKNOWN_TASK";
        }

        // 1. URL에서 파일명만 추출 (예: "1234abcd-5678.json.out")
        String fileName = s3Uri.substring(s3Uri.lastIndexOf("/") + 1);

        // 2. 첫 번째 점(.)이 등장하는 위치 앞까지만 잘라냅니다.
        // UUID에는 점이 없으므로 안전하게 순수 Task ID만 추출됩니다.
        return fileName.contains(".") ? fileName.substring(0, fileName.indexOf(".")) : fileName;
    }
}