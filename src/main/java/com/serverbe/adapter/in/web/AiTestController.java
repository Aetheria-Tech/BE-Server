package com.serverbe.adapter.in.web;

import com.serverbe.adapter.in.web.dto.sqs.SageMakerNotificationDto;
import com.serverbe.application.port.out.notification.TaskNotificationPort;
import com.serverbe.domain.exception.server.ServerErrorCode;
import com.serverbe.infrastructure.common.response.RestApiResponse;
import com.serverbe.infrastructure.config.event.AiNotificationSqsListener;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "AI Test API", description = "로컬/개발 환경 전용 AI 파이프라인 시뮬레이션 도구")
@RestController
@RequestMapping("/api/v1/test/ai/tasks")
@RequiredArgsConstructor
@Profile({"local", "dev"})
public class AiTestController {

    private final TaskNotificationPort taskNotificationPort;
    private final AiNotificationSqsListener sqsListener;

    @Operation(summary = "SSE 강제 완료 알림 발송", description = "특정 Task ID를 구독 중인 모든 클라이언트(탭)에게 AI 작업 완료 알림을 강제로 쏩니다.")
    @PostMapping("/{taskId}/push-complete")
    public ResponseEntity<RestApiResponse<String>> pushCompleteEvent(
            @Parameter(description = "구독 중인 Task ID (UUID)") @PathVariable String taskId,
            @Parameter(description = "가짜 S3 결과물 경로") @RequestParam(defaultValue = "s3://mock-bucket/dummy-result.png") String resultUri) {

        log.info("[TEST] SSE 강제 완료 이벤트 발송 - Task ID: {}", taskId);
        taskNotificationPort.notifyTaskCompleted(taskId, resultUri);

        return ResponseEntity.ok(RestApiResponse.success("성공 이벤트 발송 완료: " + taskId));
    }

    @Operation(summary = "SSE 강제 실패 알림 발송", description = "특정 Task ID를 구독 중인 모든 클라이언트(탭)에게 AI 작업 실패 알림을 강제로 쏩니다.")
    @PostMapping("/{taskId}/push-fail")
    public ResponseEntity<RestApiResponse<String>> pushFailEvent(
            @Parameter(description = "구독 중인 Task ID (UUID)") @PathVariable String taskId,
            @Parameter(description = "강제 발생시킬 에러 메시지") @RequestParam(defaultValue = "AI 연산 중 리소스 부족으로 실패했습니다.") String errorMessage
    ) {

        log.info("[TEST] SSE 강제 실패 이벤트 발송 - Task ID: {}", taskId);
        taskNotificationPort.notifyTaskFailed(taskId, errorMessage);

        return ResponseEntity.ok(RestApiResponse.success("실패 이벤트 발송 완료: " + taskId));
    }

    @Operation(summary = "SQS 리스너 로컬 시뮬레이션", description = "AWS SQS를 거치지 않고 가짜 SageMaker 알림 DTO를 리스너에 직접 주입하여 전체 비즈니스 로직(DB 저장 등)을 테스트합니다.")
    @PostMapping("/{taskId}/mock-sqs-receive")
    public ResponseEntity<RestApiResponse<?>> mockSqsReceive(
            @Parameter(description = "테스트할 Task ID (DB에 존재해야 함)") @PathVariable String taskId,
            @Parameter(description = "SageMaker 상태 (Completed 또는 Failed)") @RequestParam(defaultValue = "Completed") String status
    ) {

        log.info("[TEST] SQS 가짜 메시지 생성 및 리스너 직접 호출 - Task ID: {}", taskId);

        SageMakerNotificationDto dummyMessage = getSageMakerNotificationDto(taskId, status);

        try {
            sqsListener.receiveAiTaskNotification(dummyMessage);
            return ResponseEntity.ok(RestApiResponse.success("SQS 리스너 로컬 테스트 완료! (상태: " + status + ")\n서버 로그와 클라이언트 SSE 응답을 확인해보세요."));
        } catch (Exception e) {
            log.error("[TEST] SQS 로컬 테스트 중 에러 발생", e);
            return ResponseEntity.internalServerError().body(RestApiResponse.fail(ServerErrorCode.INTERNAL_SERVER_ERROR));
        }
    }

    private SageMakerNotificationDto getSageMakerNotificationDto(String taskId, String status) {
        SageMakerNotificationDto.ResponseParameters params =
                new SageMakerNotificationDto.ResponseParameters(
                        "application/json",
                        "s3://dummy-bucket/output/" + taskId + ".out"
                );

        String failureReason = status.equalsIgnoreCase("Failed") ? "Dummy Error: Insufficient GPU capacity" : null;

        return new SageMakerNotificationDto(
                status,
                failureReason,
                params
        );
    }
}