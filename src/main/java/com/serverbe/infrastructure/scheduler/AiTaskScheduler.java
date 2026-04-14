package com.serverbe.infrastructure.scheduler;

import com.serverbe.adapter.out.persistence.task.AiTaskEntity;
import com.serverbe.adapter.out.persistence.task.JpaAiTaskRepository;
import com.serverbe.application.service.AiResultRetrievalService;
import com.serverbe.domain.model.task.vo.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiTaskScheduler {

    private final JpaAiTaskRepository taskRepository;
    private final AiResultRetrievalService resultRetrievalService;

    /**
     * 5초마다 진행 중인 작업을 찾아 결과를 확인합니다.
     * (고도화 시 SQS Event 기반으로 전환하면 더 효율적입니다.)
     */
    @Scheduled(fixedDelay = 5000)
    public void checkProcessingTasks() {
        // 1. 진행 중인 Task 목록 조회
        List<AiTaskEntity> processingTasks = taskRepository.findAllByStatus(TaskStatus.PROCESSING);

        if (processingTasks.isEmpty()) {
            return;
        }

        log.debug("[Scheduler] 현재 진행 중인 {}건의 작업을 체크합니다.", processingTasks.size());

        // 2. 각 Task에 대해 결과 회수 시도
        for (AiTaskEntity task : processingTasks) {
            resultRetrievalService.processTaskResult(task);
        }
    }
}