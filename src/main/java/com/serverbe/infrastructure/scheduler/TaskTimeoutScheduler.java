package com.serverbe.infrastructure.scheduler;

import com.serverbe.application.port.in.task.CleanupZombieTaskUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AI 작업 파이프라인의 타임아웃을 관리하고 좀비 태스크를 주기적으로 정리하는 인프라 계층의 스케줄러.
 * <p>
 * <b>설계 원칙:</b><br>
 * 본 스케줄러는 인프라 계층(Infrastructure Layer)에 위치하며, 비즈니스 로직을 직접 수행하지 않습니다.
 * 대신 응용 계층(Application Layer)의 인바운드 포트인 {@link CleanupZombieTaskUseCase}를 호출하여
 * 제어 흐름을 도메인 로직으로 위임합니다.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskTimeoutScheduler {

    /** * 좀비 작업 정리 비즈니스 로직을 담당하는 인바운드 포트.
     * 인프라 계층이 도메인 계층의 상세 구현(JPA 등)에 직접 의존하지 않도록 인터페이스를 통해 협력합니다.
     */
    private final CleanupZombieTaskUseCase cleanupZombieTaskUseCase;

    /**
     * 5분 간격으로 좀비 작업 정리 로직을 실행합니다.
     * <p>
     * <b>스케줄링 상세:</b><br>
     * - Cron 표현식: {@code 0 0/5 * * * *} (매 5분마다 0초에 실행)<br>
     * - 대상: 실행 중(PROCESSING) 또는 대기 중(PENDING) 상태로 일정 시간(예: 10분) 이상 방치된 작업들<br>
     * - 목적: 시스템 자원 낭비 방지 및 클라이언트에게 실시간 실패 알림 보장
     * </p>
     */
    @Scheduled(cron = "0 0/5 * * * *")
    public void scheduleCleanUp() {
        log.info("[Task Scheduler] 좀비 작업 정리 프로세스 가동 (5분 주기)");

        // 실제 데이터 필터링 및 상태 변경 로직은 UseCase 구현체(Service) 내부에 격리되어 있음
        cleanupZombieTaskUseCase.cleanUpZombieTasks();

        log.info("[Task Scheduler] 좀비 작업 정리 프로세스 완료");
    }
}