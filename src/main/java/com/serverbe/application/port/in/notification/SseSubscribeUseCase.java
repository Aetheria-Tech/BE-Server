package com.serverbe.application.port.in.notification;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 클라이언트의 실시간 SSE(Server-Sent Events) 구독 요청을 처리하는 유스케이스 (Inbound Port).
 * <p>
 * 사용자가 AI 작업 생성을 요청한 직후, 해당 작업의 진행 상태 및 완료 여부를
 * 실시간으로 푸시(Push) 받기 위해 서버와 단방향 연결을 맺는 진입점입니다.
 * </p>
 */
public interface SseSubscribeUseCase {

    /**
     * 특정 AI 작업에 대한 SSE 구독(연결)을 생성하고 권한을 검증합니다.
     *
     * @param userId 구독을 요청하는 사용자의 ID (소유권 검증용)
     * @param taskId 실시간 알림을 수신할 AI 작업의 고유 ID
     * @return 클라이언트와 연결된 통로인 {@link SseEmitter} 객체
     */
    SseEmitter subscribe(Long userId, String taskId);
}