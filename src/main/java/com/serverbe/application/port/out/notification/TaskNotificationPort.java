package com.serverbe.application.port.out.notification;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * @responsibility AI 작업 완료/실패 상태를 클라이언트에게 실시간으로 알리는 아웃바운드 포트
 */
public interface TaskNotificationPort {
    SseEmitter subscribe(String taskId);
    void notifyTaskCompleted(String taskId, String resultArtId);
    void notifyTaskFailed(String taskId, String errorMessage);
}