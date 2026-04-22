package com.serverbe.application.port.in.notification;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface SseSubscribeUseCase {
    SseEmitter subscribe(Long userId, String taskId);
}