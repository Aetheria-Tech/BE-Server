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

/**
 * AI 런닝 아트 생성 파이프라인을 오케스트레이션하는 핵심 비즈니스 서비스.
 * <p>
 * WebFlux(Reactor) 환경에서 동작하며, JPA 및 외부 AWS SDK(S3, SageMaker) 호출과 같은
 * <b>블로킹(Blocking) I/O 작업을 처리할 때 Netty 이벤트 루프가 차단되는 것을 방지하기 위해
 * {@link Schedulers#boundedElastic()} 스레드 풀로 작업을 완벽히 격리</b>하여 실행합니다.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiGenerationService implements InitiateAiGenerationUseCase, GetTaskStatusUseCase {
    private final TaskQueryPort taskQueryPort;
    private final TaskUpdatePort taskUpdatePort;

    private final S3AiInputPort s3AiInputPort;
    private final SageMakerAsyncPort sageMakerAdapter;
    private final ObjectMapper objectMapper;

    /**
     * AI 생성 작업을 비동기 리액티브(Reactive) 파이프라인으로 시작합니다.
     * <p>
     * <b>파이프라인 실행 흐름:</b><br>
     * 1. <b>DB 저장 (PENDING):</b> 작업 초기 상태를 DB에 저장합니다. (boundedElastic 격리)<br>
     * 2. <b>외부 연동 (S3 & SageMaker):</b> JSON 프롬프트를 생성하여 S3에 업로드하고, SageMaker 비동기 추론을 호출합니다. (boundedElastic 격리)<br>
     * 3. <b>DB 업데이트 (PROCESSING):</b> 외부 호출이 성공하면 작업 상태를 처리 중으로 변경합니다. (boundedElastic 격리)<br>
     * 4. <b>에러 핸들링 (FAILED):</b> 파이프라인 중 예외 발생 시, 작업 상태를 실패로 기록하고 에러를 전파합니다.
     * </p>
     *
     * @param userId 요청한 사용자의 ID
     * @param startPosition 런닝 시작 위치 (예: 위도/경도 또는 주소)
     * @param shape 생성할 런닝 아트의 모양 (예: "하트", "별")
     * @param proficiency 사용자의 러닝 숙련도 (거리 및 난이도 조절용)
     * @return 파이프라인 처리가 성공적으로 접수되면 발급된 Task ID를 방출하는 {@link Mono}
     */
    @Override
    public Mono<String> initiateGeneration(
            Long userId,
            String startPosition, //TODO 시작 위치를 지오코딩으로 위도와 경도로 변환할 필요가 있음.
            String shape,
            Proficiency proficiency
    ) {
        // 1. JPA DB 저장(Blocking)을 boundedElastic 스레드풀로 격리
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
                                // 4. 에러 발생 시 실패 상태 저장 및 예외 전파
                                .onErrorResume(e -> {
                                    log.error("[AI Pipeline Error] 요청 처리 중 오류 발생 - TaskID: {}", aiTask.id(), e);

                                    return Mono.fromCallable(() -> {
                                                AiTask failedTask = aiTask.markAsFailed(e.getMessage());
                                                taskUpdatePort.save(failedTask);
                                                return failedTask; // 반환값은 무시되지만 Callable 구색 맞추기
                                            })
                                            .subscribeOn(Schedulers.boundedElastic())
                                            // DB 저장 성공 여부와 상관없이 최종적으로 비즈니스 예외를 던짐
                                            .then(Mono.error(new AiException(AiErrorCode.AI_PIPELINE_ERROR)));
                                })
                );
    }

    /**
     * 특정 AI 작업의 현재 진행 상태를 조회합니다.
     * <p>
     * 보안을 위해 해당 작업을 요청한 사용자(Owner)와 현재 조회하려는 사용자가 일치하는지 검증합니다.
     * </p>
     *
     * @param taskId 조회할 AI 작업의 고유 ID
     * @param userId 조회를 요청한 사용자의 ID
     * @return 작업의 현재 상태(상태 코드, S3 결과물 경로 등)를 담은 응답 DTO
     * @throws AiException 작업 ID가 존재하지 않거나, 본인의 작업이 아닌 경우 발생
     */
    @Override
    public TaskStatusResponse getTaskStatus(String taskId, Long userId) {
        AiTask task = taskQueryPort.findById(taskId)
                .orElseThrow(() -> new AiException(AiErrorCode.NOT_FOUND_AITASK));

        // 타인의 작업을 조회할 수 없도록 소유권 검증 로직 수행
        task.validateOwner(userId);

        return TaskStatusResponse.from(task);
    }

    /**
     * AI 모델(SageMaker)에게 전달할 요청 파라미터를 JSON 문자열로 직렬화합니다.
     *
     * @param startPosition 런닝 시작 위치
     * @param shape 목표 아트 모양
     * @param proficiency 러닝 숙련도
     * @return 직렬화된 JSON 문자열
     * @throws JsonProcessingException 객체를 JSON으로 변환하는 과정에서 오류가 발생할 경우
     */
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