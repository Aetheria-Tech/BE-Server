package com.serverbe.adapter.out.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serverbe.adapter.out.notification.dto.SsePubSubMessage;
import com.serverbe.application.port.out.notification.TaskNotificationPort;
import com.serverbe.application.port.out.task.TaskQueryPort;
import com.serverbe.infrastructure.config.properties.SseProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseNotificationAdapter implements TaskNotificationPort {

    private final Map<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final SseProperties sseProperties;

    // Redis 연동을 위한 의존성 추가
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final TaskQueryPort taskQueryPort;

    // Redis Pub/Sub 채널 이름 설정
    private static final String SSE_CHANNEL = "sse-notifications";

    @Override
    public SseEmitter subscribe(String taskId) {
        SseEmitter emitter = new SseEmitter(sseProperties.timeout());

        // 1. Map에 먼저 넣습니다
        emitters.computeIfAbsent(taskId, k -> new CopyOnWriteArraySet<>()).add(emitter);

        // 💡하나의 Emitter(탭)만 안전하게 지우는 청소 로직
        Runnable onCompletion = () -> {
            Set<SseEmitter> taskEmitters = emitters.get(taskId);
            if (taskEmitters != null) {
                taskEmitters.remove(emitter); // 이 탭의 연결만 제거
                if (taskEmitters.isEmpty()) {
                    emitters.remove(taskId); // 모든 탭이 닫히면 Map에서도 키 삭제
                }
            }
        };

        // 방금 만든 안전한 청소 로직을 콜백으로 등록합니다!
        emitter.onCompletion(onCompletion);
        emitter.onTimeout(onCompletion);
        emitter.onError((e) -> onCompletion.run());

        try {
            // 2. 연결 성공 이벤트 발송
            emitter.send(buildSseEvent("CONNECTED", "Connected. Task ID: " + taskId));

            // 3. [Race Condition 방어] DB 상태 확인
            taskQueryPort.findById(taskId).ifPresent(task -> {
                try {
                    if ("COMPLETED".equals(task.status().name())) {
                        emitter.send(buildSseEvent("COMPLETED", String.valueOf(task.resultArtId())));
                        emitter.complete();
                        log.info("[SSE] 구독 즉시 완료 상태 확인되어 알림 발송 - Task ID: {}", taskId);
                    } else if ("FAILED".equals(task.status().name())) {
                        emitter.send(buildSseEvent("FAILED", "이미 실패한 작업입니다."));
                        emitter.complete();
                        log.info("[SSE] 구독 즉시 실패 상태 확인되어 알림 발송 - Task ID: {}", taskId);
                    }
                } catch (IOException e) {
                    log.error("[SSE] 초기 상태 확인 후 알림 전송 실패 - Task ID: {}", taskId, e);
                }
            });

        } catch (IOException e) {
            // 예외가 발생했을 때도 전체를 날리지 않고 해당 Emitter만 날립니다!
            onCompletion.run();
        }
        return emitter;
    }

    // 직접 보내지 않고 Redis로 Publish(발행)
    @Override
    public void notifyTaskCompleted(String taskId, String resultArtId) {
        publishToRedis(taskId, "COMPLETED", resultArtId);
    }

    @Override
    public void notifyTaskFailed(String taskId, String errorMessage) {
        publishToRedis(taskId, "FAILED", errorMessage);
    }

    private void publishToRedis(String taskId, String eventName, String data) {
        try {
            SsePubSubMessage message = new SsePubSubMessage(taskId, eventName, data);
            String jsonMessage = objectMapper.writeValueAsString(message);

            // Redis 채널에 JSON 형태로 방송!
            redisTemplate.convertAndSend(SSE_CHANNEL, jsonMessage);
            log.info("[Redis Pub] SSE 알림 발행 완료 - Task ID: {}, Event: {}", taskId, eventName);
        } catch (Exception e) {
            log.error("[Redis Pub] 알림 발행 실패 - Task ID: {}", taskId, e);
        }
    }

    // Redis 채널에서 방송을 들었을 때 (Subscriber) 호출될 실제 발송 로직
    public void sendToClient(SsePubSubMessage message) {
        Set<SseEmitter> taskEmitters = emitters.get(message.taskId());

        if (taskEmitters != null && !taskEmitters.isEmpty()) {
            for (SseEmitter emitter : taskEmitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name(message.eventName())
                            .data(message.data()));
                    emitter.complete(); // 전송 완료 후 연결 닫기
                } catch (IOException e) {
                    taskEmitters.remove(emitter); // 전송 실패(끊긴 연결) 시 해당 Emitter만 제거
                    log.error("[SSE Sub] 단일 알림 전송 실패 - Task ID: {}", message.taskId(), e);
                }
            }
            log.info("[SSE Sub] 클라이언트 다중 알림 전송 완료! - Task ID: {}, 수신자 수: {}", message.taskId(), taskEmitters.size());
        } else {
            log.debug("[SSE Sub] 이 서버에는 해당 클라이언트 연결이 없습니다. - Task ID: {}", message.taskId());
        }
    }

    private SseEmitter.SseEventBuilder buildSseEvent(String eventName, String data){
        return SseEmitter.event()
                .name(eventName)
                .data(data);
    }
}