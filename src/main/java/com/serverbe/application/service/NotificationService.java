package com.serverbe.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Service
public class NotificationService {

    private final String discordWebhookUrl;
    private final WebClient webClient;

    public NotificationService(
            WebClient.Builder webClientBuilder,
            @Value("${notification.discord.webhook-url}") String discordWebhookUrl
    ) {
        this.webClient = webClientBuilder.build();
        this.discordWebhookUrl = discordWebhookUrl;
    }

    public void sendDiscordNotification(String message) {
        if (discordWebhookUrl == null || discordWebhookUrl.isBlank()) {
            log.warn("웹훅 URL이 설정되지 않아 알림을 보낼 수 없습니다.");
            return;
        }

        try {
            Map<String, String> payload = Collections.singletonMap("content", message);

            // WebClient를 이용한 비동기 POST 요청
            webClient.post()
                    .uri(discordWebhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Void.class) // 응답 본문은 필요 없으므로 Void
                    .subscribe(             // subscribe()를 호출해야 실제 비동기 요청이 실행됨 (Fire-and-Forget)
                            success -> log.info("디스코드 알림 전송 완료"),
                            error -> log.error("디스코드 알림 전송 중 오류 발생: {}", error.getMessage())
                    );

        } catch (Exception e) {
            log.error("디스코드 알림 페이로드 생성 중 오류 발생: {}", e.getMessage());
        }
    }
}