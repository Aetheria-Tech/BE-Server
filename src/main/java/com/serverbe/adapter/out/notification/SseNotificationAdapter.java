package com.serverbe.adapter.out.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serverbe.adapter.out.notification.dto.SseNotificationDto;
import com.serverbe.adapter.out.notification.dto.SsePubSubMessage;
import com.serverbe.application.port.out.notification.TaskNotificationPort;
import com.serverbe.application.port.out.task.TaskQueryPort;
import com.serverbe.infrastructure.config.properties.SseProperties;
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
 * <b>주요 설계 특징:</b><br>
 * 1. <b>Scale-out 지원:</b> 다중 서버 환경에서 클라이언트가 어느 서버에 연결되더라도 정상적으로 알림을 받을 수 있도록 Redis Pub/Sub 아키텍처를 활용합니다.<br>
 * 2. <b>스레드 안전성(Thread-safety):</b> 다수의 클라이언트 요청을 안전하게 처리하기 위해 {@link ConcurrentHashMap}과 {@link CopyOnWriteArraySet}을 사용합니다.<br>
 * 3. <b>일관된 응답 규격:</b> 모든 SSE 데이터는 프론트엔드와의 통신 규약인 {@link SseNotificationDto} 형태로 JSON 직렬화되어 발송됩니다.
 * </p>
 */
@Slf4j
@Component
public class SseNotificationAdapter implements TaskNotificationPort {

    /**
     * Task ID를 Key로 하여, 해당 Task를 구독 중인 클라이언트들의 SseEmitter 목록을 관리합니다.
     * <p>
     * 한 사용자가 여러 탭/기기에서 동시 접속할 수 있으므로 Value는 단일 객체가 아닌 Set 컬렉션을 사용합니다.
     * </p>
     */
    private final Map<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseNotificationAdapter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            TaskQueryPort taskQueryPort,
            SseProperties sseProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.taskQueryPort = taskQueryPort;
        this.timeout = sseProperties.timeout();
        this.sseChannel = sseProperties.channel();
    }

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TaskQueryPort taskQueryPort;

    private final String sseChannel;
    private final Long timeout;

    /**
     * 클라이언트의 SSE 구독 요청을 처리하고 새로운 연결({@link SseEmitter})을 반환합니다.
     * <p>
     * <b>주요 처리 흐름:</b><br>
     * 1. <b>연결 관리:</b> 메모리 누수를 방지하기 위해 연결 종료/타임아웃/에러 시 컬렉션에서 안전하게 제거되는 콜백을 등록합니다.<br>
     * 2. <b>초기 이벤트 발송:</b> 클라이언트에게 연결 성공(CONNECTED) 상태를 {@link SseNotificationDto} 포맷으로 발송합니다.<br>
     * 3. <b>Race Condition 방어:</b> 알림 발생과 구독 요청 사이의 미세한 시간 차이로 인한 알림 유실을 방지하기 위해 DB 상태를 즉시 조회합니다.
     * </p>
     *
     * @param taskId 구독할 대상인 AI 작업의 고유 ID
     * @return 생성 및 초기화된 SseEmitter 객체
     */
    @Override
    public SseEmitter subscribe(String taskId) {
        SseEmitter emitter = new SseEmitter(timeout);

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
            // 단순 문자열 대신 DTO를 사용하여 JSON으로 발송
            SseNotificationDto connectedDto = SseNotificationDto.processing(taskId, "SSE 연결이 완료되었습니다. AI 생성 대기 중...");
            emitter.send(buildSseEvent("CONNECTED", connectedDto));

            // 초기 상태 즉시 확인 (Race condition 방어)
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

    /**
     * AI 작업이 성공적으로 완료되었음을 다중 서버에 전파합니다.
     * 실제 클라이언트 전송은 Redis Pub/Sub 수신부({@link #sendToClient})에서 처리됩니다.
     *
     * @param taskId 완료된 AI 작업의 ID
     * @param resultArtId 생성된 런닝 아트의 고유 ID
     */
    @Override
    public void notifyTaskCompleted(String taskId, String resultArtId) {
        publishToRedis(taskId, "COMPLETED", resultArtId);
    }

    /**
     * AI 작업이 실패했음을 다중 서버에 전파합니다.
     * 실제 클라이언트 전송은 Redis Pub/Sub 수신부({@link #sendToClient})에서 처리됩니다.
     *
     * @param taskId 실패한 AI 작업의 ID
     * @param errorMessage 실패 원인 또는 에러 메시지
     */
    @Override
    public void notifyTaskFailed(String taskId, String errorMessage) {
        publishToRedis(taskId, "FAILED", errorMessage);
    }

    /**
     * 내부 메시지 객체를 JSON으로 직렬화하여 Redis Pub/Sub 채널로 발행(Publish)합니다.
     *
     * @param taskId 대상 작업 ID
     * @param eventName 발생한 이벤트 유형
     * @param data 클라이언트에 전달할 식별자 또는 메시지
     */
    private void publishToRedis(String taskId, String eventName, String data) {
        try {
            SsePubSubMessage message = new SsePubSubMessage(taskId, eventName, data);
            String jsonMessage = objectMapper.writeValueAsString(message);

            redisTemplate.convertAndSend(sseChannel, jsonMessage);
            log.info("[Redis Pub] SSE 알림 발행 완료 - Task ID: {}, Event: {}", taskId, eventName);
        } catch (Exception e) {
            log.error("[Redis Pub] 알림 발행 실패 - Task ID: {}", taskId, e);
        }
    }

    /**
     * Redis 채널에서 메시지를 수신(Subscribe)했을 때 호출되는 메서드입니다.
     * 현재 서버의 메모리에 접속되어 있는(Map에 존재하는) 클라이언트들을 찾아 실제 SSE 이벤트를 발송합니다.
     * <p>
     * 수신된 데이터를 프론트엔드 규격인 {@link SseNotificationDto}로 변환하며,
     * 터미널 상태(COMPLETED, FAILED)인 경우에만 SSE 연결을 명시적으로 종료합니다.
     * </p>
     *
     * @param message Redis로부터 수신한 역직렬화된 SSE 메시지 객체
     */
    public void sendToClient(SsePubSubMessage message) {
        Set<SseEmitter> taskEmitters = emitters.get(message.taskId());

        if (taskEmitters != null && !taskEmitters.isEmpty()) {

            SseNotificationDto notificationDto;
            boolean isTerminalState = false; // 종료(마지막) 상태인지 판단하는 플래그

            // Redis에서 넘어온 데이터를 프론트엔드 규격으로 포장 및 상태 판별
            if ("COMPLETED".equals(message.eventName())) {
                notificationDto = SseNotificationDto.completed(message.taskId(), message.data());
                isTerminalState = true;
            } else if ("FAILED".equals(message.eventName())) {
                notificationDto = SseNotificationDto.failed(message.taskId(), message.data());
                isTerminalState = true;
            } else {
                notificationDto = SseNotificationDto.processing(message.taskId(), message.data());
            }

            for (SseEmitter emitter : taskEmitters) {
                try {
                    // 객체(DTO)를 그대로 넘기면 Spring이 JSON으로 자동 직렬화 수행
                    emitter.send(buildSseEvent(message.eventName(), notificationDto));

                    // 터미널 상태(COMPLETED, FAILED)일 때만 SSE 연결을 닫음 (확장성 고려)
                    if (isTerminalState) {
                        emitter.complete();
                    }
                } catch (IOException e) {
                    taskEmitters.remove(emitter);
                    log.error("[SSE Sub] 단일 알림 전송 실패 - Task ID: {}", message.taskId(), e);
                }
            }
            log.info("[SSE Sub] 클라이언트 다중 알림 전송 완료! - Task ID: {}, 수신자 수: {}, 연결 종료 여부: {}",
                    message.taskId(), taskEmitters.size(), isTerminalState);
        } else {
            log.debug("[SSE Sub] 이 서버에는 해당 클라이언트 연결이 없습니다. - Task ID: {}", message.taskId());
        }
    }

    /**
     * SseEmitter를 통해 전송할 이벤트 객체를 생성하는 헬퍼 메서드입니다.
     * <p>
     * data 파라미터로 {@link SseNotificationDto} 객체를 전달받아,
     * Spring HttpMessageConverter를 통해 자동으로 JSON 직렬화가 이루어지도록 돕습니다.
     * </p>
     *
     * @param eventName 이벤트의 이름
     * @param data JSON으로 변환되어 클라이언트에게 전달될 페이로드 객체
     * @return 구성된 SseEventBuilder 객체
     */
    private SseEmitter.SseEventBuilder buildSseEvent(String eventName, Object data) {
        return SseEmitter.event()
                .name(eventName)
                .data(data);
    }
}