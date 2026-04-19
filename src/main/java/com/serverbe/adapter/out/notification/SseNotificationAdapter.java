package com.serverbe.adapter.out.notification;

import com.serverbe.application.port.out.notification.TaskNotificationPort;
import com.serverbe.infrastructure.config.properties.SseProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor // SseProperties 주입을 위해 추가
public class SseNotificationAdapter implements TaskNotificationPort {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    // 외부 프로퍼티(yml)에서 주입받은 설정
    private final SseProperties sseProperties;

    /**
     * [구독 - 포트 구현] 클라이언트가 처음 연결을 맺을 때 Emitter를 생성하고 저장합니다.
     */
    @Override
    public SseEmitter subscribe(String taskId) {
        // 객체 생성은 매 요청마다 하되, 타임아웃 값은 프로퍼티에서 동적으로 가져옵니다!
        SseEmitter emitter = new SseEmitter(sseProperties.timeout());
        emitters.put(taskId, emitter);

        emitter.onCompletion(() -> emitters.remove(taskId));
        emitter.onTimeout(() -> emitters.remove(taskId));
        emitter.onError((e) -> emitters.remove(taskId));

        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECT")
                    .data("Connected successfully. Task ID: " + taskId));
        } catch (IOException e) {
            emitters.remove(taskId);
            log.error("[SSE] 초기 연결 이벤트 전송 실패 - Task ID: {}", taskId, e);
        }

        return emitter;
    }

    @Override
    public void notifyTaskCompleted(String taskId, String resultS3Uri) {
        SseEmitter emitter = emitters.get(taskId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("COMPLETED")
                        .data(resultS3Uri));
                log.info("[SSE] 작업 완료 알림 Push 성공 - Task ID: {}", taskId);
                emitter.complete();
            } catch (IOException e) {
                emitters.remove(taskId);
                log.error("[SSE] 완료 알림 Push 실패 - Task ID: {}", taskId, e);
            }
        } else {
            log.warn("[SSE] 알림을 보낼 연결된 클라이언트가 없습니다. - Task ID: {}", taskId);
        }
    }

    @Override
    public void notifyTaskFailed(String taskId, String errorMessage) {
        SseEmitter emitter = emitters.get(taskId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("FAILED")
                        .data(errorMessage));
                emitter.complete();
            } catch (IOException e) {
                emitters.remove(taskId);
            }
        }
    }
}