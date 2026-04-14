package com.serverbe.infrastructure.scheduler;

import com.serverbe.application.port.in.task.CleanupZombieTaskUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskTimeoutScheduler {

    // JPA Repository가 아닌 UseCase(인바운드 포트)를 의존합니다.
    private final CleanupZombieTaskUseCase cleanupZombieTaskUseCase;

    @Scheduled(cron = "0 0/5 * * * *")
    public void scheduleCleanUp() {
        // 비즈니스 로직은 애플리케이션 계층(Service)에 완전히 위임!
        cleanupZombieTaskUseCase.cleanUpZombieTasks();
    }
}