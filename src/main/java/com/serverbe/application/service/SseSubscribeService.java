package com.serverbe.application.service;

import com.serverbe.application.port.in.notification.SseSubscribeUseCase;
import com.serverbe.application.port.out.notification.TaskNotificationPort;
import com.serverbe.application.port.out.task.TaskQueryPort;
import com.serverbe.domain.exception.ai.AiErrorCode;
import com.serverbe.domain.exception.ai.AiException;
import com.serverbe.domain.model.task.AiTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SseSubscribeService implements SseSubscribeUseCase {

    private final TaskNotificationPort taskNotificationPort;
    private final TaskQueryPort taskQueryPort;

    @Override
    public SseEmitter subscribe(Long userId, String taskId) {
        AiTask aiTask = taskQueryPort.findById(taskId).orElseThrow(() -> new AiException(AiErrorCode.NOT_FOUND_AITASK));

        if(!Objects.equals(aiTask.userId(), userId)) {
            throw new AiException(AiErrorCode.USER_IS_NOT_OWNER_OF_TASK);
        }

        return taskNotificationPort.subscribe(taskId);
    }
}