package com.serverbe.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/*
 * 전체 컨텍스트와 외부 Webhook이 필요한 알림 발송 테스트.
 * 기본 `gradlew test` 에서는 제외되며 `gradlew integrationTest` 로만 실행됩니다.
 */
@Tag("integration")
@SpringBootTest
class NotificationServiceTest {

    @Autowired
    private NotificationService notificationService;

    @Test
    @DisplayName("디스코드 웹훅으로 실제 테스트 알림을 전송한다")
    void sendRealDiscordMessage() {
        // given & when
        notificationService.sendDiscordNotification("🧪 **[테스트]** 스프링 부트 통합 테스트에서 날아온 웹훅 메시지입니다! 성공!");

        // then: 핸드폰이나 PC에서 디스코드 알림이 오는지 확인하세요!
    }
}