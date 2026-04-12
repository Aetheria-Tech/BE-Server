package com.serverbe.application.service;

import com.fasterxml.jackson.databind.ObjectMapper; // JSON 변환용
import com.serverbe.adapter.in.web.dto.task.TaskStatusResponse;
import com.serverbe.adapter.out.persistence.task.AiTaskEntity;
import com.serverbe.adapter.out.persistence.task.JpaAiTaskRepository;
import com.serverbe.application.port.in.art.InitiateAiGenerationUseCase;
import com.serverbe.application.port.in.task.GetTaskStatusUseCase;
import com.serverbe.application.port.out.sagemaker.SageMakerAsyncPort;
import com.serverbe.application.port.out.s3.S3AiInputPort;
import com.serverbe.domain.model.art.vo.Proficiency;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiGenerationService implements InitiateAiGenerationUseCase, GetTaskStatusUseCase { // ✨ UseCase 구현

    private final JpaAiTaskRepository taskRepository;
    private final S3AiInputPort s3AiInputPort;
    private final SageMakerAsyncPort sageMakerAdapter;
    private final ObjectMapper objectMapper; // JSON 생성기

    @Override
    @Transactional
    public String initiateGeneration(Long userId, String startPosition, String shape, Proficiency proficiency) {

        // 1. 대기 상태의 Task 레코드 생성
        AiTaskEntity task = AiTaskEntity.builder()
                .userId(userId)
                .build();
        AiTaskEntity savedTask = taskRepository.save(task);
        String taskId = savedTask.getId();

        try {
            // 2. 입력받은 도메인 데이터를 AI 모델이 이해할 수 있는 JSON으로 변환
            String promptJson = buildPromptJson(startPosition, shape, proficiency);

            // 3. S3에 요청 JSON 업로드
            String inputS3Uri = s3AiInputPort.uploadInputJson(taskId, promptJson);

            // 4. SageMaker 비동기 엔드포인트 호출
            String outputS3Uri = sageMakerAdapter.invokeAsync(inputS3Uri);

            // 5. Task 정보 업데이트
            savedTask.markAsProcessing(inputS3Uri, outputS3Uri);

            return taskId;

        } catch (Exception e) {
            log.error("[AI Pipeline Error] 요청 처리 중 오류", e);
            savedTask.markAsFailed(e.getMessage());
            throw new RuntimeException("AI 생성 요청을 처리할 수 없습니다.", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TaskStatusResponse getTaskStatus(String taskId, Long userId) {
        AiTaskEntity task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("작업을 찾을 수 없습니다."));
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