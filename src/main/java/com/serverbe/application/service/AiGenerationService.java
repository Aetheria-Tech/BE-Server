package com.serverbe.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serverbe.adapter.in.web.dto.task.TaskStatusResponse;
import com.serverbe.application.port.in.art.InitiateAiGenerationUseCase;
import com.serverbe.application.port.in.task.GetTaskStatusUseCase;
import com.serverbe.application.port.out.sagemaker.SageMakerAsyncPort;
import com.serverbe.application.port.out.s3.S3AiInputPort;
import com.serverbe.application.port.out.task.TaskQueryPort;
import com.serverbe.application.port.out.task.TaskUpdatePort;
import com.serverbe.domain.exception.ai.AiErrorCode;
import com.serverbe.domain.exception.ai.AiException;
import com.serverbe.domain.model.art.vo.Proficiency;
import com.serverbe.domain.model.task.AiTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiGenerationService implements InitiateAiGenerationUseCase, GetTaskStatusUseCase {
    private final TaskQueryPort taskQueryPort;
    private final TaskUpdatePort taskUpdatePort;

    private final S3AiInputPort s3AiInputPort;
    private final SageMakerAsyncPort sageMakerAdapter;
    private final ObjectMapper objectMapper;

    @Override
    public String initiateGeneration(Long userId, String startPosition, String shape, Proficiency proficiency) {

        // 1. 순수 도메인 객체 생성 (Entity 의존성 완벽 제거)
        // UUID 발급 등은 도메인 모델 내부 팩토리 메서드에서 처리한다고 가정합니다.
        AiTask pendingTask = AiTask.createPending(userId, shape, proficiency);

        // 2. 초기 상태(PENDING) 단기 트랜잭션으로 DB 저장
        taskUpdatePort.save(pendingTask);
        String taskId = pendingTask.id();

        try {
            // 3. 외부 API 연동 (트랜잭션 밖이므로 병목 없음)
            String promptJson = buildPromptJson(startPosition, shape, proficiency);
            String inputS3Uri = s3AiInputPort.uploadInputJson(taskId, promptJson);
            String outputS3Uri = sageMakerAdapter.invokeAsync(inputS3Uri);

            // 4. 상태 업데이트 후 저장 (도메인 메서드 사용)
            AiTask processingTask = pendingTask.markAsProcessing(inputS3Uri, outputS3Uri);
            taskUpdatePort.save(processingTask);

            return taskId;

        } catch (Exception e) {
            log.error("[AI Pipeline Error] 요청 처리 중 오류", e);

            // 5. 실패 상태 기록 (트랜잭션이 분리되어 있으므로 예외가 터져도 롤백되지 않고 DB에 정상 반영됨!)
            AiTask failedTask = pendingTask.markAsFailed(e.getMessage());
            taskUpdatePort.save(failedTask);

            throw new AiException(AiErrorCode.AI_PIPELINE_ERROR);
        }
    }

    @Override
    public TaskStatusResponse getTaskStatus(String taskId, Long userId) {
        AiTask task = taskQueryPort.findById(taskId)
                .orElseThrow(() -> new AiException(AiErrorCode.NOT_FOUND_AITASK));

        task.validateOwner(userId);

        return TaskStatusResponse.from(task);
    }

    private String buildPromptJson(String startPosition, String shape, Proficiency proficiency) throws Exception {
        Map<String, Object> promptData = Map.of(
                "start_position", startPosition,
                "shape", shape,
                "proficiency", proficiency.name()
        );
        return objectMapper.writeValueAsString(promptData);
    }
}