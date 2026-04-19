package com.serverbe.adapter.in.web;

import com.serverbe.adapter.in.web.dto.sqs.SageMakerNotificationDto;
import com.serverbe.application.port.out.notification.TaskNotificationPort;
import com.serverbe.infrastructure.config.event.AiNotificationSqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/test/ai/tasks")
@RequiredArgsConstructor
@Profile({"local", "dev"})
public class AiTestController {

    // 테스트용이므로 UseCase를 거치지 않고 바로 Outbound Port(Adapter)를 찔러 강제 발송합니다.
    private final TaskNotificationPort taskNotificationPort;
    private final AiNotificationSqsListener sqsListener;

    /**
     * [강제 성공 알림] 특정 Task ID를 구독 중인 클라이언트에게 완료 알림을 쏩니다.
     */
    @PostMapping("/{taskId}/push-complete")
    public ResponseEntity<String> pushCompleteEvent(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "s3://mock-bucket/dummy-result.png") String resultUri) {
        
        log.info("[TEST] SSE 강제 완료 이벤트 발송 - Task ID: {}", taskId);
        taskNotificationPort.notifyTaskCompleted(taskId, resultUri);
        
        return ResponseEntity.ok("성공 이벤트 발송 완료: " + taskId);
    }

    /**
     * [강제 실패 알림] 특정 Task ID를 구독 중인 클라이언트에게 에러 알림을 쏩니다.
     */
    @PostMapping("/{taskId}/push-fail")
    public ResponseEntity<String> pushFailEvent(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "AI 연산 중 리소스 부족으로 실패했습니다.") String errorMessage) {
        
        log.info("[TEST] SSE 강제 실패 이벤트 발송 - Task ID: {}", taskId);
        taskNotificationPort.notifyTaskFailed(taskId, errorMessage);
        
        return ResponseEntity.ok("실패 이벤트 발송 완료: " + taskId);
    }

    /**
     * [SQS 로컬 시뮬레이션] 형님의 실제 DTO 규격에 맞춘 가짜 메시지 주입
     */
    @PostMapping("/{taskId}/mock-sqs-receive")
    public ResponseEntity<String> mockSqsReceive(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "Completed") String status) {

        log.info("[TEST] SQS 가짜 메시지 생성 및 리스너 직접 호출 - Task ID: {}", taskId);

        // 1. 알려주신 실제 DTO 구조에 맞게 ResponseParameters 객체 조립
        // (contentType은 임의로 넣고, URI는 파싱하기 좋게 taskId를 포함시킵니다)
        SageMakerNotificationDto.ResponseParameters params =
                new SageMakerNotificationDto.ResponseParameters(
                        "application/json",
                        "s3://dummy-bucket/output/" + taskId + ".out"
                );

        // 2. 실패일 경우에만 failureReason을 넣도록 처리
        String failureReason = status.equalsIgnoreCase("Failed") ? "Dummy Error: Insufficient GPU capacity" : null;

        // 3. 실제 DTO 구조에 맞게 SageMakerNotificationDto 객체 생성
        SageMakerNotificationDto dummyMessage = new SageMakerNotificationDto(
                status,
                failureReason,
                params
        );

        // 4. AWS SQS를 거치지 않고, 리스너 메서드를 직접 호출!
        try {
            sqsListener.receiveAiTaskNotification(dummyMessage); // 형님의 리스너 메서드 이름에 맞게 수정해주세요
            return ResponseEntity.ok("SQS 리스너 로컬 테스트 완료! (상태: " + status + ")\n서버 로그와 클라이언트 SSE 응답을 확인해보세요.");
        } catch (Exception e) {
            log.error("[TEST] SQS 로컬 테스트 중 에러 발생", e);
            return ResponseEntity.internalServerError().body("에러 발생: " + e.getMessage());
        }
    }
}