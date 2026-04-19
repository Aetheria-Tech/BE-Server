package com.serverbe.application.service;

import com.serverbe.application.port.in.task.UpdateTaskStatusUseCase;
import com.serverbe.application.port.out.notification.TaskNotificationPort;
import com.serverbe.application.port.out.task.TaskQueryPort;
import com.serverbe.application.port.out.task.TaskUpdatePort;

import com.serverbe.domain.exception.ai.AiErrorCode;
import com.serverbe.domain.exception.ai.AiException;
import com.serverbe.domain.model.task.AiTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTaskStatusUpdateService implements UpdateTaskStatusUseCase {

    private final TaskQueryPort taskQueryPort;
    private final TaskUpdatePort taskUpdatePort;
    private final TaskNotificationPort taskNotificationPort;

    @Override
    @Transactional
    public void completeTask(String taskId, String resultS3Uri) {
        // 1. 도메인 객체 조회 (TaskQueryPort에 findById 메서드가 필요합니다)
        AiTask task = taskQueryPort.findById(taskId)
                .orElseThrow(() -> new AiException(AiErrorCode.NOT_FOUND_AITASK));

        // 2. 비즈니스 로직: 도메인 상태를 완료로 변경 (불변 객체이므로 새 객체 반환)
        // (참고: 도메인에 결과물 ID나 URI를 매핑하는 로직에 맞게 인자를 넘겨줍니다)
        AiTask completedTask = task.markAsCompleted(null); 

        // 3. DB 상태 업데이트 (JPA Dirty Checking 또는 Save)
        taskUpdatePort.save(completedTask);
        log.info("[Service] DB 업데이트 완료 - Task ID: {}", taskId);

        // 4. SSE 알림 발송 명령 (어댑터가 SseEmitter를 찾아 전송함)
        taskNotificationPort.notifyTaskCompleted(taskId, resultS3Uri);
    }

    @Override
    @Transactional
    public void failTask(String taskId, String errorMessage) {
        AiTask task = taskQueryPort.findById(taskId)
                .orElseThrow(() -> new AiException(AiErrorCode.NOT_FOUND_AITASK));

        AiTask failedTask = task.markAsFailed(errorMessage);
        taskUpdatePort.save(failedTask);
        
        log.warn("[Service] Task 실패 처리 완료 - Task ID: {}, 사유: {}", taskId, errorMessage);

        taskNotificationPort.notifyTaskFailed(taskId, errorMessage);
    }
}