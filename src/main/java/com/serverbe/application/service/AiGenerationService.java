package com.serverbe.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
    public Mono<String> initiateGeneration(
            Long userId,
            String startPosition,
            String shape,
            Proficiency proficiency
    ) {
        // 1. [가이드 30번 준수] JPA DB 저장(Blocking)을 boundedElastic 스레드풀로 격리
        return Mono.fromCallable(() -> taskUpdatePort.save(AiTask.createPending(userId, shape, proficiency)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(aiTask ->
                        // 2. [가이드 8번 준수] 외부 I/O (S3, SageMaker) 작업 격리
                        Mono.fromCallable(() -> {
                                    String promptJson = buildPromptJson(startPosition, shape, proficiency);
                                    String inputS3Uri = s3AiInputPort.uploadInputJson(aiTask.id(), promptJson);
                                    String outputS3Uri = sageMakerAdapter.invokeAsync(inputS3Uri);
                                    return aiTask.markAsProcessing(inputS3Uri, outputS3Uri);
                                })
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(processingTask ->
                                        // 3. 상태 업데이트를 위한 DB 저장 (다시 격리)
                                        Mono.fromCallable(() -> {
                                            taskUpdatePort.save(processingTask);
                                            return aiTask.id();
                                        }).subscribeOn(Schedulers.boundedElastic())
                                )
                                // 4. 에러 발생 시 실패 상태 저장
                                .onErrorResume(e -> {
                                    log.error("[AI Pipeline Error] 요청 처리 중 오류", e);

                                    return Mono.fromCallable(() -> {
                                                AiTask failedTask = aiTask.markAsFailed(e.getMessage());
                                                taskUpdatePort.save(failedTask);
                                                return failedTask; // 반환값은 무시되지만 Callable 구색 맞추기
                                            })
                                            .subscribeOn(Schedulers.boundedElastic())
                                            // DB 저장 성공 여부와 상관없이 최종적으로 예외 던지기
                                            .then(Mono.error(new AiException(AiErrorCode.AI_PIPELINE_ERROR)));
                                })
                );
    }

    @Override
    public TaskStatusResponse getTaskStatus(String taskId, Long userId) {
        AiTask task = taskQueryPort.findById(taskId)
                .orElseThrow(() -> new AiException(AiErrorCode.NOT_FOUND_AITASK));

        task.validateOwner(userId);

        return TaskStatusResponse.from(task);
    }

    private String buildPromptJson(
            String startPosition,
            String shape,
            Proficiency proficiency
    ) throws JsonProcessingException {
        Map<String, Object> promptData = Map.of(
                "start_position", startPosition,
                "shape", shape,
                "proficiency", proficiency.name()
        );
        return objectMapper.writeValueAsString(promptData);
    }
}