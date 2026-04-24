package com.serverbe.adapter.in.web;

import com.serverbe.application.port.in.notification.SseSubscribeUseCase;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/ai/tasks")
@RequiredArgsConstructor
public class SseController {

    private final SseSubscribeUseCase sseSubscribeUseCase;

    @GetMapping(value = "/{taskId}/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@Parameter(hidden = true) @AuthenticationPrincipal Long userId, @PathVariable String taskId) {
        return sseSubscribeUseCase.subscribe(userId, taskId);
    }
}