package com.serverbe.adapter.out.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serverbe.adapter.out.notification.dto.SsePubSubMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseRedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final SseNotificationAdapter sseNotificationAdapter;

    /**
     * Redis 채널에 메시지가 발행되면 이 메서드가 자동으로 실행됩니다.
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 1. Redis에서 날아온 JSON 메시지를 읽습니다.
            String body = new String(message.getBody());
            
            // 2. 우리가 만든 DTO 객체로 변환합니다.
            SsePubSubMessage sseMessage = objectMapper.readValue(body, SsePubSubMessage.class);
            
            // 3. Adapter에게 "이 메시지 온 거 있으면 클라이언트한테 쏴줘!" 라고 시킵니다.
            sseNotificationAdapter.sendToClient(sseMessage);
            
        } catch (Exception e) {
            log.error("[Redis Sub] 메시지 수신/파싱 중 에러 발생", e);
        }
    }
}