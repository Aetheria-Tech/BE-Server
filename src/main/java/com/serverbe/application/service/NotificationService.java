package com.serverbe.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class NotificationService {

    @Value("${notification.discord.webhook-url}")
    private String discordWebhookUrl;

    // Discord API는 JSON 형태로 {"content": "보낼 메시지"} 를 받습니다.
    // Slack의 경우 {"text": "보낼 메시지"} 를 사용합니다.
    public void sendDiscordNotification(String message) {
        if (discordWebhookUrl == null || discordWebhookUrl.isBlank()) {
            log.warn("웹훅 URL이 설정되지 않아 알림을 보낼 수 없습니다.");
            return;
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // JSON 페이로드 생성 (따옴표 이스케이프 처리)
            String payload = "{\"content\": \"" + message + "\"}";
            
            HttpEntity<String> entity = new HttpEntity<>(payload, headers);
            
            // 디스코드 웹훅 URL로 POST 요청 전송
            restTemplate.postForEntity(discordWebhookUrl, entity, String.class);
            log.info("디스코드 알림 전송 완료");
            
        } catch (Exception e) {
            log.error("디스코드 알림 전송 중 오류 발생: {}", e.getMessage());
        }
    }
}