package com.serverbe.adapter.out.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serverbe.adapter.out.notification.dto.SsePubSubMessage;
import com.serverbe.application.port.out.notification.TaskNotificationPort;
import com.serverbe.infrastructure.config.properties.SseProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseNotificationAdapter implements TaskNotificationPort {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final SseProperties sseProperties;

    // 💡 Redis 연동을 위한 의존성 추가
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // Redis Pub/Sub 채널 이름 설정
    private static final String SSE_CHANNEL = "sse-notifications";

    @Override
    public SseEmitter subscribe(String taskId) {
        SseEmitter emitter = new SseEmitter(sseProperties.timeout());
        emitters.put(taskId, emitter);

        emitter.onCompletion(() -> emitters.remove(taskId));
        emitter.onTimeout(() -> emitters.remove(taskId));
        emitter.onError((e) -> emitters.remove(taskId));

        try {
            emitter.send(SseEmitter.event().name("CONNECT").data("Connected. Task ID: " + taskId));
        } catch (IOException e) {
            emitters.remove(taskId);
        }
        return emitter;
    }

    // 직접 보내지 않고 Redis로 Publish(발행) 합니다!
    @Override
    public void notifyTaskCompleted(String taskId, String resultS3Uri) {
        publishToRedis(taskId, "COMPLETED", resultS3Uri);
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
        SseEmitter emitter = emitters.get(message.taskId());

        // 내 서버에 Emitter가 있을 때만 쏜다! (다른 서버에 있으면 null이므로 무시됨)
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name(message.eventName())
                        .data(message.data()));
                log.info("[SSE Sub] 클라이언트로 알림 전송 성공! - Task ID: {}", message.taskId());
                emitter.complete();
            } catch (IOException e) {
                emitters.remove(message.taskId());
                log.error("[SSE Sub] 알림 전송 실패 - Task ID: {}", message.taskId(), e);
            }
        } else {
            // 이 로그는 정상입니다! 유저가 다른 서버에 붙어있다는 뜻입니다.
            log.debug("[SSE Sub] 이 서버에는 해당 클라이언트 연결이 없습니다. - Task ID: {}", message.taskId());
        }
    }
}