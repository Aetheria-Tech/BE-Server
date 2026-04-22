package com.serverbe.application.service;

import com.serverbe.application.port.in.task.CleanupZombieTaskUseCase;
import com.serverbe.application.port.out.task.TaskUpdatePort;
import com.serverbe.application.port.out.task.TaskQueryPort;
import com.serverbe.domain.model.task.AiTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTaskCleanupService implements CleanupZombieTaskUseCase {

    private final TaskQueryPort taskQueryPort;
    private final TaskUpdatePort taskUpdatePort;

    @Override
    @Transactional
    public void cleanUpZombieTasks() {
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(10);

        // 포트를 통해 도메인 객체(AiTask)를 가져옵니다.
        List<AiTask> zombieTasks = taskQueryPort.findZombieTasks(timeoutThreshold);

        if (!zombieTasks.isEmpty()) {
            log.warn("[AI Pipeline] {}개의 좀비 Task FAILED 처리", zombieTasks.size());

            for (AiTask task : zombieTasks) {
                AiTask failedTask = task.markAsFailed("AI 서버 응답 시간 초과 (Timeout)");
                taskUpdatePort.save(failedTask);
            }
        }
    }
}