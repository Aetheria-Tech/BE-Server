package com.serverbe.adapter.out.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serverbe.adapter.out.notification.dto.SseNotificationDto;
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

/**
 * AI 작업 상태 실시간 알림을 위한 SSE (Server-Sent Events) 어댑터 구현체.
 * <p>
 * 다중 서버 환경(Scale-out)에서 어떤 서버에 클라이언트가 연결되어 있더라도
 * 정상적으로 알림을 받을 수 있도록 <b>Redis Pub/Sub</b> 아키텍처를 활용합니다.
 * 모든 SSE 데이터는 프론트엔드와의 규약인 {@link SseNotificationDto} 형태로 JSON 직렬화되어 발송됩니다.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseNotificationAdapter implements TaskNotificationPort {

    private final Map<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final SseProperties sseProperties;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TaskQueryPort taskQueryPort;

    private static final String SSE_CHANNEL = "sse-notifications";

    @Override
    public SseEmitter subscribe(String taskId) {
        SseEmitter emitter = new SseEmitter(sseProperties.timeout());

        emitters.computeIfAbsent(taskId, k -> new CopyOnWriteArraySet<>()).add(emitter);

        Runnable onCompletion = () -> {
            emitters.computeIfPresent(taskId, (key, taskEmitters) -> {
                taskEmitters.remove(emitter);
                return taskEmitters.isEmpty() ? null : taskEmitters;
            });
        };

        emitter.onCompletion(onCompletion);
        emitter.onTimeout(onCompletion);
        emitter.onError((e) -> onCompletion.run());

        try {
            //  단순 문자열 대신 DTO를 사용하여 JSON으로 발송합니다!
            SseNotificationDto connectedDto = SseNotificationDto.processing(taskId, "SSE 연결이 완료되었습니다. AI 생성 대기 중...");
            emitter.send(buildSseEvent("CONNECTED", connectedDto));

            taskQueryPort.findById(taskId).ifPresent(task -> {
                try {
                    if ("COMPLETED".equals(task.status().name())) {
                        SseNotificationDto completedDto = SseNotificationDto.completed(taskId, String.valueOf(task.resultArtId()));
                        emitter.send(buildSseEvent("COMPLETED", completedDto));
                        emitter.complete();
                        log.info("[SSE] 구독 즉시 완료 상태 확인되어 알림 발송 - Task ID: {}", taskId);
                    } else if ("FAILED".equals(task.status().name())) {
                        SseNotificationDto failedDto = SseNotificationDto.failed(taskId, "이미 실패한 작업입니다.");
                        emitter.send(buildSseEvent("FAILED", failedDto));
                        emitter.complete();
                        log.info("[SSE] 구독 즉시 실패 상태 확인되어 알림 발송 - Task ID: {}", taskId);
                    }
                } catch (IOException e) {
                    log.error("[SSE] 초기 상태 확인 후 알림 전송 실패 - Task ID: {}", taskId, e);
                }
            });

        } catch (IOException e) {
            onCompletion.run();
        }
        return emitter;
    }

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
            // Redis Pub/Sub 용 내부 메시지 객체는 그대로 유지
            SsePubSubMessage message = new SsePubSubMessage(taskId, eventName, data);
            String jsonMessage = objectMapper.writeValueAsString(message);

            redisTemplate.convertAndSend(SSE_CHANNEL, jsonMessage);
            log.info("[Redis Pub] SSE 알림 발행 완료 - Task ID: {}, Event: {}", taskId, eventName);
        } catch (Exception e) {
            log.error("[Redis Pub] 알림 발행 실패 - Task ID: {}", taskId, e);
        }
    }

    public void sendToClient(SsePubSubMessage message) {
        Set<SseEmitter> taskEmitters = emitters.get(message.taskId());

        if (taskEmitters != null && !taskEmitters.isEmpty()) {

            // Redis에서 넘어온 데이터를 프론트엔드 규격인 SseNotificationDto로 변환
            SseNotificationDto notificationDto;
            if ("COMPLETED".equals(message.eventName())) {
                notificationDto = SseNotificationDto.completed(message.taskId(), message.data());
            } else if ("FAILED".equals(message.eventName())) {
                notificationDto = SseNotificationDto.failed(message.taskId(), message.data());
            } else {
                notificationDto = SseNotificationDto.processing(message.taskId(), message.data());
            }

            for (SseEmitter emitter : taskEmitters) {
                try {
                    // 객체(DTO)를 그대로 넘기면 Spring이 JSON으로 자동 직렬화합니다!
                    emitter.send(buildSseEvent(message.eventName(), notificationDto));
                    emitter.complete();
                } catch (IOException e) {
                    taskEmitters.remove(emitter);
                    log.error("[SSE Sub] 단일 알림 전송 실패 - Task ID: {}", message.taskId(), e);
                }
            }
            log.info("[SSE Sub] 클라이언트 다중 알림 전송 완료! - Task ID: {}, 수신자 수: {}", message.taskId(), taskEmitters.size());
        } else {
            log.debug("[SSE Sub] 이 서버에는 해당 클라이언트 연결이 없습니다. - Task ID: {}", message.taskId());
        }
    }

    /**
     * data 파라미터를 String에서 Object로 변경했습니다.
     * DTO 객체를 넘기면 HttpMessageConverter가 개입하여 JSON 포맷으로 자동 변환하여 클라이언트에게 쏩니다.
     */
    private SseEmitter.SseEventBuilder buildSseEvent(String eventName, Object data) {
        return SseEmitter.event()
                .name(eventName)
                .data(data);
    }
}