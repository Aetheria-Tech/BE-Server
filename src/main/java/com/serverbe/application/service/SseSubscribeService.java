package com.serverbe.application.service;

import com.serverbe.application.port.in.notification.SseSubscribeUseCase;
import com.serverbe.application.port.out.notification.TaskNotificationPort;
import com.serverbe.application.port.out.task.TaskQueryPort;
import com.serverbe.domain.exception.ai.AiErrorCode;
import com.serverbe.domain.exception.ai.AiException;
import com.serverbe.domain.model.task.AiTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;

/**
 * 클라이언트의 SSE(Server-Sent Events) 구독 요청을 처리하는 비즈니스 서비스 구현체.
 * <p>
 * AI 생성 작업의 진행 상태를 실시간으로 클라이언트에게 푸시(Push)하기 위해 사용됩니다.
 * 어댑터(Adapter) 계층으로 구독 처리를 위임하기 전에,
 * 해당 작업을 요청한 <b>본인(Owner)이 맞는지 검증하는 인가(Authorization) 과정</b>을 철저히 수행하여 보안을 강화합니다.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class SseSubscribeService implements SseSubscribeUseCase {

    private final TaskNotificationPort taskNotificationPort;
    private final TaskQueryPort taskQueryPort;

    /**
     * 특정 AI 작업(Task)에 대한 SSE 연결을 생성하고 구독을 시작합니다.
     * <p>
     * <b>처리 흐름:</b><br>
     * 1. <b>작업 조회:</b> 전달받은 {@code taskId}로 DB에서 작업 엔티티를 조회합니다.<br>
     * 2. <b>권한 검증:</b> 작업을 생성한 사용자의 ID와 현재 구독을 요청한 {@code userId}가 일치하는지 확인합니다. (타인 작업 구독 방지)<br>
     * 3. <b>구독 위임:</b> 검증이 통과되면 Notification Port를 통해 실제 {@link SseEmitter} 객체를 생성하고 반환합니다.
     * </p>
     *
     * @param userId 구독을 요청한 클라이언트의 사용자 ID
     * @param taskId 실시간 알림을 받을 AI 작업의 고유 ID
     * @return 클라이언트와의 연결을 유지하고 이벤트를 전송할 수 있는 {@link SseEmitter} 객체
     * @throws AiException 대상 작업이 존재하지 않거나({@code NOT_FOUND_AITASK}), 요청한 사용자가 작업의 소유자가 아닌 경우({@code USER_IS_NOT_OWNER_OF_TASK}) 발생
     */
    @Override
    public SseEmitter subscribe(Long userId, String taskId) {
        // 1. Task 존재 여부 확인
        AiTask aiTask = taskQueryPort.findById(taskId)
                .orElseThrow(() -> new AiException(AiErrorCode.NOT_FOUND_AITASK));

        // 2. 권한 검증 (Security/Authorization)
        if (!Objects.equals(aiTask.userId(), userId)) {
            throw new AiException(AiErrorCode.USER_IS_NOT_OWNER_OF_TASK);
        }

        // 3. 실제 SSE 구독 생성 및 반환
        return taskNotificationPort.subscribe(taskId);
    }
}