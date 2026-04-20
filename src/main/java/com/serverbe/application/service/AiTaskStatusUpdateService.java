package com.serverbe.application.service;

import com.serverbe.application.port.in.art.RegisterCompletedArtUseCase;
import com.serverbe.application.port.in.task.UpdateTaskStatusUseCase;
import com.serverbe.application.port.out.notification.TaskNotificationPort;
import com.serverbe.application.port.out.task.TaskQueryPort;
import com.serverbe.application.port.out.task.TaskUpdatePort;

import com.serverbe.domain.exception.ai.AiErrorCode;
import com.serverbe.domain.exception.ai.AiException;
import com.serverbe.domain.model.art.vo.Proficiency;
import com.serverbe.domain.model.task.AiTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTaskStatusUpdateService implements UpdateTaskStatusUseCase {

    private final TaskQueryPort taskQueryPort;
    private final TaskUpdatePort taskUpdatePort;
    private final TaskNotificationPort taskNotificationPort;
    private final RegisterCompletedArtUseCase registerCompletedArtUseCase;

    // 💡 테스트용 가짜 Polyline 데이터
    // AI가 S3에 저장해둘 압축된 형태의 문자열입니다. 프론트엔드 지도 라이브러리에서 바로 디코딩 가능합니다.
    private static final String MOCK_POLYLINE_DATA = "}obwEu{|eW_ibE_ibE~hbE~hbE";

    @Override
    @Transactional // ✨ 이제 하나의 스레드에서 돌아가므로 트랜잭션이 보장됩니다!
    public void completeTask(String taskId, String resultS3Uri) {
        log.info("[Service] Task 완료 처리 시작 - Task ID: {}", taskId);

        // 1. Task 조회 (동기 방식)
        AiTask task = taskQueryPort.findById(taskId)
                .orElseThrow(() -> new AiException(AiErrorCode.NOT_FOUND_AITASK));

        // 2. 런닝 아트 등록 (동기 호출 - 내부에서 DB 저장 및 Redis block() 처리)
        // 메서드명을 registerFromPolyline으로 바꾸셨다면 그에 맞게 호출하세요!
        Long generatedArtId = registerCompletedArtUseCase.registerFromPolyline(
                task.userId(),
                MOCK_POLYLINE_DATA,
                "테스트 런닝 아트",
                "STAR",
                Proficiency.BEGINNER
        );

        // 3. Task 상태 갱신 (도메인 모델 로직)
        AiTask completedTask = task.markAsCompleted(generatedArtId);

        // 4. DB 저장 (JPA Dirty Checking 또는 Save)
        taskUpdatePort.save(completedTask);

        // 5. SSE 알림 발송 (이건 트랜잭션 커밋 직후에 보내는 것이 베스트지만 일단 여기서 호출)
        taskNotificationPort.notifyTaskCompleted(taskId, resultS3Uri);

        log.info("[Service] Task 완료 및 런닝아트 연결 성공 - Art ID: {}", generatedArtId);
    }

    @Override
    @Transactional
    public void failTask(String taskId, String errorMessage) {
        AiTask task = taskQueryPort.findById(taskId)
                .orElseThrow(() -> new AiException(AiErrorCode.NOT_FOUND_AITASK));

        AiTask failedTask = task.markAsFailed(errorMessage);
        taskUpdatePort.save(failedTask);

        log.warn("[Service] Task 실패 처리 완료 - Task ID: {}, 사유: {}", taskId, errorMessage);

        taskNotificationPort.notifyTaskFailed(taskId, errorMessage);
    }
}