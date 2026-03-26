package com.serverbe.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest // ✅ 핵심: 진짜 스프링 부트를 띄웁니다!
class NotificationServiceTest {

    // ✅ 스프링이 떴기 때문에 @Autowired가 완벽하게 작동합니다.
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