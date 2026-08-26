package com.serverbe.infrastructure.config.event;

import com.serverbe.adapter.in.web.dto.sqs.SageMakerNotificationDto;
import com.serverbe.application.port.in.task.RetrieveAiResultUseCase;
import com.serverbe.application.port.out.notification.TaskNotificationPort;
import com.serverbe.application.port.out.task.TaskQueryPort;
import com.serverbe.application.port.out.task.TaskUpdatePort;
import com.serverbe.application.service.helper.AiTaskResourceCleaner;
import com.serverbe.domain.exception.server.AsyncRaceConditionException;
import com.serverbe.domain.exception.server.DataIntegrityViolationException;
import com.serverbe.domain.exception.server.ServerErrorCode;
import com.serverbe.domain.model.task.AiTask;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

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
    private final AiTaskResourceCleaner resourceCleaner;

    private static final String QUEUE_NAME_PROPERTY = "${aws.sqs.ai-notification-queue-name}";

    /** SageMaker 알림에서 Task ID를 끝내 특정하지 못했을 때 사용하는 표식. */
    private static final String UNKNOWN_TASK = "UNKNOWN_TASK";

    /**
     * SQS 큐에서 SageMaker의 처리 결과 메시지를 수신하여 상태에 맞는 후속 처리를 수행합니다.
     * <p>
     * <b>주요 처리 흐름:</b><br>
     * 1. <b>Task 특정 및 락 획득:</b> 메시지에서 Task ID를 추출하고 비관적 락으로 조회하여 동시 접근을 직렬화합니다.<br>
     * 2. <b>경합 조건 방어(PENDING):</b> 요청 스레드가 아직 {@code PROCESSING} 저장을 마치지 못한 상태라면
     * 예외를 던져 SQS 가시성 타임아웃 이후 재시도되도록 유도합니다.<br>
     * 3. <b>멱등성 보장(종결 상태):</b> 이미 {@code COMPLETED}/{@code FAILED}로 끝난 작업이면 재처리하지 않고 종료합니다.
     * 단, 그 사이 뒤늦게 도착한 고아 결과물이 스토리지 비용으로 남지 않도록 S3 정리는 수행합니다.<br>
     * 4. <b>성공/실패 분기:</b> 성공이면 결과물 등록 파이프라인을 트리거하고, 실패면 상태를 FAILED로 기록한 뒤
     * 임시 자원을 정리하고 클라이언트에 실패 알림을 발송합니다.<br>
     * 5. <b>안전망(DLQ):</b> 처리 중 예외는 그대로 전파하여 최종적으로 DLQ로 이동시켜 메시지 유실을 방지합니다.
     * </p>
     *
     * @param message SQS로부터 수신하여 역직렬화된 SageMaker 알림 이벤트 DTO
     */
    @Transactional // 비관적 락을 유지하기 위해 반드시 트랜잭션이 필요합니다!
    @SqsListener(QUEUE_NAME_PROPERTY)
    public void receiveAiTaskNotification(SageMakerNotificationDto message) {
        String taskId = extractTaskId(message);

        try {
            AiTask task = getTaskWithLockOrThrow(taskId);

            // [경합 조건 방어] 메인 스레드가 아직 PROCESSING으로 상태를 업데이트하지 못했다면 재시도를 유도합니다.
            if (task.isPending()) {
                throw new AsyncRaceConditionException(
                        ServerErrorCode.ASYNC_RACE_CONDITION,
                        String.format("Task [%s] 가 아직 PENDING 상태입니다. 메인 스레드 업데이트 지연으로 판단하여 예외를 발생시키고 SQS 재시도를 유도합니다.", task.id())
                );
            }

            // [멱등성 보장] 이미 종결된 작업은 절대 재처리하지 않고, 남아 있을 수 있는 임시 자원만 정리합니다.
            if (!task.isProcessable()) {
                log.info("[SQS Listener] Task {} 는 이미 종결된 작업입니다(현재 상태: {}). 중복/지연 메시지로 판단하여 재처리하지 않습니다.",
                        task.id(), task.status());
                cleanUpResourcesAfterCommit(task);
                return;
            }

            if (message.isCompleted()) {
                retrieveAiResultUseCase.processTaskResult(task);
            } else {
                handleInferenceFailure(task, message.failureReason());
            }

        } catch (AsyncRaceConditionException e) {
            // PENDING 상태로 인한 재시도 유도 예외는 WARN 레벨로 로깅 (정상적인 시스템 흐름 중 하나)
            log.warn("[SQS Listener] 비동기 경합 조건(Race Condition) 발생 - {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            // 그 외 심각한 에러는 ERROR 레벨로 로깅
            log.error("[SQS Listener] SQS 메시지 처리 중 오류 - TaskID: {}", taskId, e);
            throw e;
        }
    }

    /**
     * SageMaker 추론 자체가 실패했을 때의 후속 처리를 수행합니다.
     * <p>
     * 상태를 FAILED로 기록하고, 이 작업이 S3에 남긴 입력 프롬프트와 실패 출력물을 정리한 뒤,
     * 대기 중인 클라이언트에게 실패 사실을 즉시 알립니다. 추론이 실패해도 S3 객체는 그대로 남아
     * 비용이 계속 발생하므로 <b>정리는 실패 경로에서 더 중요합니다.</b>
     * </p>
     *
     * @param task   FAILED로 전환할 대상 작업 (비관적 락으로 조회된 상태)
     * @param reason SageMaker가 전달한 실패 사유
     */
    private void handleInferenceFailure(AiTask task, String reason) {
        AiTask failedTask = task.markAsFailed("SageMaker 추론 실패: " + reason);
        taskUpdatePort.save(failedTask);

        cleanUpResourcesAfterCommit(failedTask);
        taskNotificationPort.notifyTaskFailed(failedTask.id(), reason);
    }

    /**
     * S3 임시 자원 정리를 <b>트랜잭션 커밋 이후로</b> 미룹니다.
     * <p>
     * 정리는 네트워크 I/O이므로 트랜잭션 안에서 수행하면 그동안 DB 커넥션과 비관적 락을 붙잡게 됩니다.
     * 또한 트랜잭션이 롤백되어 작업이 여전히 살아 있는데도 파일만 먼저 지워버리는 정합성 문제도 생깁니다.
     * 커밋이 확정된 뒤에만 삭제하도록 동기화를 등록합니다.
     * </p>
     */
    private void cleanUpResourcesAfterCommit(AiTask task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 트랜잭션 없이 호출된 경우(테스트 등)에는 즉시 정리합니다.
            resourceCleaner.cleanUp(task);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                resourceCleaner.cleanUp(task);
            }
        });
    }

    /**
     * Task ID를 기반으로 DB에서 도메인 엔티티를 비관적 락과 함께 조회합니다.
     *
     * @param taskId 조회할 AI 작업의 고유 ID
     * @return 조회된 {@link AiTask} 도메인 객체
     * @throws DataIntegrityViolationException SQS 이벤트는 도착했으나 DB에 해당 Task 기록이 없는 심각한 비동기 정합성 오류 발생 시
     */
    private AiTask getTaskWithLockOrThrow(String taskId) {
        return taskQueryPort.findByIdForUpdate(taskId)
                .orElseThrow(() -> new DataIntegrityViolationException(
                        ServerErrorCode.RESOURCE_NOT_FOUND,
                        String.format("SQS 이벤트 수신 심각한 오류: DB에서 Task ID [%s]를 찾을 수 없습니다. (원인 예상: 트랜잭션 롤백, 백그라운드 스케줄러에 의한 삭제, 또는 동시성 문제)", taskId)
                ));
    }

    /**
     * SageMaker 알림 메시지에서 대상 Task ID를 특정합니다.
     * <p>
     * <b>추출 우선순위:</b><br>
     * 1. <b>{@code inferenceId}</b> — 추론 요청 시 우리가 Task ID를 그대로 넣어 보낸 값으로, SageMaker가
     * <b>성공/실패 알림 모두에</b> 그대로 되돌려줍니다. 가장 신뢰할 수 있는 출처입니다.<br>
     * 2. <b>결과물 S3 경로 파싱</b> — {@code inferenceId}가 없는 이전 형식의 메시지를 위한 하위 호환 경로입니다.
     * </p>
     * <p>
     * 실패 알림은 성공 알림과 페이로드 구조가 달라 결과물 경로가 아예 없을 수 있습니다. 경로 파싱에만
     * 의존하면 <b>추론 실패가 DB에 기록되지 못한 채 전량 DLQ로 빠지는</b> 문제가 생기므로 {@code inferenceId}를 우선합니다.
     * </p>
     */
    private String extractTaskId(SageMakerNotificationDto message) {
        if (StringUtils.hasText(message.inferenceId())) {
            return message.inferenceId();
        }

        String taskId = extractTaskIdFromUri(message.outputLocation());
        if (UNKNOWN_TASK.equals(taskId)) {
            log.error("[SQS Listener] 메시지에서 Task ID를 특정할 수 없습니다. inferenceId와 결과물 경로가 모두 비어 있습니다. (상태: {}, 사유: {})",
                    message.invocationStatus(), message.failureReason());
        }
        return taskId;
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
            return UNKNOWN_TASK;
        }

        // 1. URL에서 파일명만 추출 (예: "1234abcd-5678.json.out")
        String fileName = s3Uri.substring(s3Uri.lastIndexOf("/") + 1);

        if (!StringUtils.hasText(fileName)) {
            return UNKNOWN_TASK;
        }

        // 2. 첫 번째 점(.)이 등장하는 위치 앞까지만 잘라냅니다.
        return fileName.contains(".") ? fileName.substring(0, fileName.indexOf(".")) : fileName;
    }
}
