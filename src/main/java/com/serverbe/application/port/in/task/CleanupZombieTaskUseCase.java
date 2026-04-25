package com.serverbe.application.port.in.task;

/**
 * 방치된 좀비(Zombie) AI 작업을 정리하는 유스케이스 (Inbound Port).
 * <p>
 * 주로 스케줄러(Scheduler) 등 외부 트리거에 의해 주기적으로 호출되어,
 * 시스템의 자가 치유(Self-Healing) 프로세스를 시작하는 비즈니스 진입점 역할을 합니다.
 * </p>
 */
public interface CleanupZombieTaskUseCase {

    /**
     * 임계 시간을 초과하여 멈춰 있는 작업들을 색인하고 일괄적으로 실패(FAILED) 처리합니다.
     */
    void cleanUpZombieTasks();
}