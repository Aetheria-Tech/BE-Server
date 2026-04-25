package com.serverbe.adapter.out.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serverbe.adapter.out.notification.dto.SsePubSubMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub 채널을 구독(Subscribe)하여 브로드캐스팅된 메시지를 수신하는 리스너 컴포넌트.
 * <p>
 * 다중 서버(Scale-out) 환경에서는 AI 작업 완료 이벤트를 발생시킨 서버(예: Server A)와
 * 클라이언트가 실제 SSE 연결을 맺고 있는 서버(예: Server B)가 다를 수 있습니다.
 * 이를 해결하기 위해 이 리스너는 Redis 채널을 지속적으로 바라보며 메시지가 수신되면,
 * 역직렬화 후 {@link SseNotificationAdapter}에 위임하여 현재 서버에 연결된 클라이언트에게 알림을 쏘아줍니다.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseRedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final SseNotificationAdapter sseNotificationAdapter;

    /**
     * Redis 채널에 새로운 메시지가 발행(Publish)되었을 때 Spring Data Redis에 의해 자동으로 호출되는 콜백 메서드입니다.
     *
     * @param message Redis로부터 수신한 원본 메시지 객체 (바이트 배열 형태의 본문 및 채널 정보 포함)
     * @param pattern 구독 중인 채널의 패턴 정보 (패턴 매칭을 사용하지 않은 경우 보통 채널명과 동일)
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 1. Redis에서 날아온 바이트 배열 형태의 메시지 본문을 UTF-8 문자열로 변환합니다.
            String body = new String(message.getBody());

            // 2. 수신된 JSON 문자열을 애플리케이션 내부에서 사용하는 DTO 객체로 역직렬화(파싱)합니다.
            SsePubSubMessage sseMessage = objectMapper.readValue(body, SsePubSubMessage.class);

            // 3. 변환된 메시지를 어댑터에 전달하여, 현재 서버가 관리 중인 해당 Emitter(클라이언트)가 있다면 이벤트를 전송하도록 지시합니다.
            sseNotificationAdapter.sendToClient(sseMessage);

        } catch (Exception e) {
            log.error("[Redis Sub] 메시지 수신/파싱 중 에러 발생. 메시지 처리 실패.", e);
        }
    }
}