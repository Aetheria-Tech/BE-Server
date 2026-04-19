package com.serverbe.application.service;

import com.serverbe.application.port.in.notification.SseSubscribeUseCase;
import com.serverbe.application.port.out.notification.TaskNotificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
public class SseSubscribeService implements SseSubscribeUseCase {

    private final TaskNotificationPort taskNotificationPort;

    @Override
    public SseEmitter subscribe(String taskId) {
        // TODO: 필요한 경우 여기서 유효성 검사나 권한 체크 로직 수행
        // 예: taskQueryPort.findById(taskId)로 존재 여부 확인 등
        
        return taskNotificationPort.subscribe(taskId);
    }
}