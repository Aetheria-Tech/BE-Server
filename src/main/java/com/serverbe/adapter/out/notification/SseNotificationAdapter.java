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

/**
 * AI 작업 상태 실시간 알림을 위한 SSE (Server-Sent Events) 어댑터 구현체.
 * <p>
 * 다중 서버 환경(Scale-out)에서 어떤 서버에 클라이언트가 연결되어 있더라도
 * 정상적으로 알림을 받을 수 있도록 <b>Redis Pub/Sub</b> 아키텍처를 활용합니다.
 * 또한, 멀티스레드 환경에서 Emitter 객체들을 안전하게 관리하기 위해
 * {@link ConcurrentHashMap}과 {@link CopyOnWriteArraySet}을 사용합니다.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseNotificationAdapter implements TaskNotificationPort {

    /**
     * Task ID를 Key로 하여, 해당 Task를 구독 중인 클라이언트들의 SseEmitter 목록을 관리하는 스레드 안전한 Map.
     * 한 사용자가 여러 탭/기기에서 동시 접속할 수 있으므로 Value는 Set 컬렉션을 사용합니다.
     */
    private final Map<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final SseProperties sseProperties;

    // Redis 연동을 위한 의존성 추가
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final TaskQueryPort taskQueryPort;

    /**
     * 다중 서버 간 SSE 이벤트를 브로드캐스팅하기 위한 Redis Pub/Sub 채널명
     */
    private static final String SSE_CHANNEL = "sse-notifications";

    /**
     * 클라이언트의 SSE 구독을 처리하고 새로운 {@link SseEmitter}를 반환합니다.
     * <p>
     * 구독 시 다음 세 가지 주요 작업을 수행합니다:
     * 1. 연결 및 타임아웃 종료 시 컬렉션에서 안전하게 제거되도록 콜백을 원자적(Atomic)으로 등록합니다.
     * 2. 클라이언트에게 초기 연결 성공(CONNECTED) 이벤트를 발송합니다.
     * 3. DB 상태를 즉시 조회하여, 알림 발생과 구독 요청 사이의 <b>Race Condition(경쟁 상태)</b>으로 인한 알림 유실을 방지합니다.
     * </p>
     *
     * @param taskId 구독할 대상인 AI 작업의 고유 ID
     * @return 생성 및 초기화된 SseEmitter 객체
     */
    @Override
    public SseEmitter subscribe(String taskId) {
        SseEmitter emitter = new SseEmitter(sseProperties.timeout());

        // 1. Map에 먼저 넣습니다
        emitters.computeIfAbsent(taskId, k -> new CopyOnWriteArraySet<>()).add(emitter);

        // 하나의 Emitter(탭)만 안전하게 지우는 청소 로직
        Runnable onCompletion = () -> {
            emitters.computeIfPresent(taskId, (key, taskEmitters) -> {
                taskEmitters.remove(emitter); // 이 탭의 연결만 제거

                // Set이 비었으면 null을 반환하여 Map에서 해당 Key를 원자적으로 삭제!
                // (비어있지 않다면 기존 taskEmitters를 그대로 유지)
                return taskEmitters.isEmpty() ? null : taskEmitters;
            });
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

    /**
     * 작업이 성공적으로 완료되었음을 알립니다. (Redis 채널로 Publish)
     *
     * @param taskId 완료된 AI 작업의 ID
     * @param resultArtId 생성된 런닝 아트의 고유 ID (데이터)
     */
    @Override
    public void notifyTaskCompleted(String taskId, String resultArtId) {
        publishToRedis(taskId, "COMPLETED", resultArtId);
    }

    /**
     * 작업이 실패했음을 알립니다. (Redis 채널로 Publish)
     *
     * @param taskId 실패한 AI 작업의 ID
     * @param errorMessage 실패 원인 또는 에러 메시지
     */
    @Override
    public void notifyTaskFailed(String taskId, String errorMessage) {
        publishToRedis(taskId, "FAILED", errorMessage);
    }

    /**
     * Redis Pub/Sub 채널로 SSE 이벤트를 직렬화하여 발행(Publish)합니다.
     *
     * @param taskId 이벤트 대상 작업 ID
     * @param eventName 이벤트 종류 (COMPLETED, FAILED 등)
     * @param data 클라이언트에 전달할 페이로드 데이터
     */
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

    /**
     * Redis 채널에서 메시지를 수신(Subscribe)했을 때 호출되는 리스너 메서드입니다.
     * 현재 서버에 접속되어 있는(Map에 존재하는) 클라이언트들에게 실제 SSE 이벤트를 발송합니다.
     *
     * @param message Redis로부터 수신한 역직렬화된 SSE 메시지 객체
     */
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

    /**
     * SseEmitter를 통해 전송할 이벤트 객체를 생성하는 헬퍼 메서드입니다.
     *
     * @param eventName 이벤트의 이름 (예: CONNECTED, COMPLETED)
     * @param data 이벤트와 함께 전달할 문자열 데이터
     * @return 구성된 SseEventBuilder 객체
     */
    private SseEmitter.SseEventBuilder buildSseEvent(String eventName, String data){
        return SseEmitter.event()
                .name(eventName)
                .data(data);
    }
}