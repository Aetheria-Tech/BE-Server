package com.serverbe.infrastructure.config.event;

import com.serverbe.adapter.in.web.dto.sqs.SageMakerNotificationDto;
import com.serverbe.application.port.in.task.RetrieveAiResultUseCase;
import com.serverbe.application.port.out.notification.TaskNotificationPort;
import com.serverbe.application.port.out.task.TaskQueryPort;
import com.serverbe.application.port.out.task.TaskUpdatePort;
import com.serverbe.domain.exception.server.DataIntegrityViolationException;
import com.serverbe.domain.exception.server.ServerErrorCode;
import com.serverbe.domain.model.task.AiTask;
import com.serverbe.domain.model.task.vo.TaskStatus;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * AWS SQS 큐를 구독하여 비동기 AI 워커(SageMaker)의 작업 완료 및 실패 이벤트를 수신하는 인바운드 어댑터(이벤트 리스너).
 * <p>
 * AI 모델이 비동기 추론을 마치고 결과물을 S3에 업로드한 뒤 SQS를 통해 콜백(Callback) 이벤트를 발행하면,
 * 이 컴포넌트가 메시지를 소비(Consume)하여 핵심 비즈니스 파이프라인({@link RetrieveAiResultUseCase})을 트리거합니다.
 * </p>
 */
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
     * SQS 큐에서 SageMaker의 처리 결과 메시지를 수신하여 상태에 맞는 후속 처리를 수행합니다.
     * <p>
     * <b>주요 처리 흐름:</b><br>
     * 1. <b>성공 (Completed):</b> 결과물이 저장된 S3 URI에서 Task ID를 추출하고, {@link RetrieveAiResultUseCase}를 호출하여 결과물 다운로드 및 DB 저장을 위임합니다.<br>
     * 2. <b>실패 (Failed):</b> 실패 사유를 추출하여 DB의 Task 상태를 FAILED로 업데이트하고, 클라이언트에게 즉시 실패 SSE 알림을 발송합니다.<br>
     * 3. <b>안전망 (DLQ 처리):</b> 로직 실행 중 예외가 발생할 경우 이를 무시(catch 후 로깅만 수행)하지 않고 외부로 던집니다(throw). 이를 통해 SQS의 가시성 타임아웃(Visibility Timeout) 이후 재시도되거나 최종적으로 DLQ(Dead Letter Queue)로 이동하여 메시지 유실을 완벽히 방지합니다.
     * </p>
     *
     * @param message SQS로부터 수신하여 역직렬화된 SageMaker 알림 이벤트 DTO
     * @throws Exception DB 조회 실패 또는 비즈니스 로직 처리 중 에러 발생 시 (SQS 재시도 유발용)
     */
    @Transactional // 🚨 락을 유지하기 위해 반드시 트랜잭션이 필요합니다!
    @SqsListener(QUEUE_NAME_PROPERTY)
    public void receiveAiTaskNotification(SageMakerNotificationDto message) {
        try {
            if ("Completed".equalsIgnoreCase(message.invocationStatus())) {
                String outputS3Uri = message.responseParameters().outputLocation();
                String taskId = extractTaskIdFromUri(outputS3Uri);

                // 1. 헬퍼 메서드를 통해 락 걸고 조회
                AiTask task = getTaskWithLockOrThrow(taskId);

                // 2. 멱등성 검증
                if (task.status() != TaskStatus.PROCESSING) {
                    log.info("[SQS Listener] Task {} 는 이미 완료/실패 상태입니다. 중복 메시지 무시.", taskId);
                    return;
                }

                // 3. 도메인 객체 전달
                retrieveAiResultUseCase.processTaskResult(task);

            } else {
                // 실패 로직
                String failedS3Uri = message.responseParameters().outputLocation();
                String taskId = extractTaskIdFromUri(failedS3Uri);

                // 1. 🔒 실패 로직에서도 동일한 헬퍼 메서드 사용 (락 유지)
                AiTask task = getTaskWithLockOrThrow(taskId);

                // 2. 🛡️ 멱등성 검증
                if (task.status() != TaskStatus.PROCESSING) {
                    log.info("[SQS Listener] Task {} 는 이미 완료/실패 상태입니다. 중복 메시지 무시.", taskId);
                    return;
                }

                // 3. 후속 실패 처리
                String reason = message.failureReason();
                AiTask failedTask = task.markAsFailed("SageMaker 추론 실패: " + reason);
                taskUpdatePort.save(failedTask);
                taskNotificationPort.notifyTaskFailed(taskId, reason);
            }
        } catch (Exception e) {
            log.error("[SQS Listener] SQS 메시지 처리 중 오류", e);
            throw e;
        }
    }

    /**
     * Task ID를 기반으로 DB에서 도메인 엔티티를 조회하고, 존재하지 않을 경우 데이터 무결성 예외를 발생시키는 헬퍼 메서드.
     *
     * @param taskId 조회할 AI 작업의 고유 ID
     * @return 조회된 {@link AiTask} 도메인 객체
     * @throws DataIntegrityViolationException SQS 이벤트는 도착했으나 DB에 해당 Task 기록이 없는 심각한 비동기 정합성 오류 발생 시
     */
    private AiTask getTaskWithLockOrThrow(String taskId) {
        return taskQueryPort.findByIdForUpdate(taskId) // 🔥 핵심: 일반 findById가 아니라 ForUpdate 호출!
                .orElseThrow(() -> new DataIntegrityViolationException(
                        ServerErrorCode.RESOURCE_NOT_FOUND,
                        String.format("SQS 이벤트 수신 심각한 오류: DB에서 Task ID [%s]를 찾을 수 없습니다. (원인 예상: 트랜잭션 롤백, 백그라운드 스케줄러에 의한 삭제, 또는 동시성 문제)", taskId)
                ));
    }

    /**
     * SageMaker가 생성한 결과물 S3 URI 문자열에서 순수한 Task ID(UUID)만을 안전하게 파싱합니다.
     * <p>
     * SageMaker의 Asynchronous Inference는 원본 입력 파일명 뒤에 {@code .out} 등의 추가 확장자를 임의로
     * 붙이는 특성이 있습니다. (예: {@code 1234-abcd.json.out}). 이 메서드는 파일명에서 첫 번째 점({@code .})이
     * 등장하기 전까지의 문자열만 잘라내어 원본 UUID를 복원합니다.
     * </p>
     *
     * @param s3Uri 결과물이 저장된 S3 전체 경로 (예: {@code s3://bucket/outputs/uuid.json.out})
     * @return 파싱된 순수 Task ID 문자열. 유효하지 않은 URI인 경우 {@code UNKNOWN_TASK} 반환
     */
    private String extractTaskIdFromUri(String s3Uri) {
        if (s3Uri == null || !s3Uri.contains("/")) {
            return "UNKNOWN_TASK";
        }

        // 1. URL에서 파일명만 추출 (예: "1234abcd-5678.json.out")
        String fileName = s3Uri.substring(s3Uri.lastIndexOf("/") + 1);

        // 2. 첫 번째 점(.)이 등장하는 위치 앞까지만 잘라냅니다.
        return fileName.contains(".") ? fileName.substring(0, fileName.indexOf(".")) : fileName;
    }
}