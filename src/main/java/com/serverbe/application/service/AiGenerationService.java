package com.serverbe.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serverbe.adapter.in.web.dto.task.TaskStatusResponse;
import com.serverbe.application.port.in.art.InitiateAiGenerationUseCase;
import com.serverbe.application.port.in.task.GetTaskStatusUseCase;
import com.serverbe.application.port.out.dto.geocoding.GeocodeResult;
import com.serverbe.application.port.out.geocode.GeocodePort;
import com.serverbe.application.port.out.sagemaker.SageMakerAsyncPort;
import com.serverbe.application.port.out.s3.S3AiInputPort;
import com.serverbe.application.port.out.task.TaskQueryPort;
import com.serverbe.application.port.out.task.TaskUpdatePort;
import com.serverbe.domain.exception.ai.AiErrorCode;
import com.serverbe.domain.exception.ai.AiException;
import com.serverbe.domain.exception.external.ExternalApiClientException;
import com.serverbe.domain.exception.external.ExternalApiErrorCode;
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
    private final GeocodePort geocodePort;

    private final S3AiInputPort s3AiInputPort;
    private final SageMakerAsyncPort sageMakerAdapter;
    private final ObjectMapper objectMapper;

    /**
     * AI 생성 작업을 비동기 리액티브(Reactive) 파이프라인으로 시작합니다.
     * <p>
     * <b>파이프라인 실행 흐름:</b><br>
     * 1. <b>입력값 검증 (Geocoding):</b> 주소를 위경도로 변환하며 유효성을 가장 먼저 검증합니다. (Fail-Fast)<br>
     * 2. <b>DB 저장 (PENDING):</b> 검증을 통과하면 작업 초기 상태를 DB에 저장합니다.<br>
     * 3. <b>외부 연동 (S3 & SageMaker):</b> JSON 프롬프트를 생성하여 S3에 업로드하고, SageMaker 비동기 추론을 호출합니다.<br>
     * 4. <b>DB 업데이트 (PROCESSING):</b> 외부 호출이 성공하면 작업 상태를 처리 중으로 변경합니다.<br>
     * 5. <b>에러 핸들링 (FAILED):</b> 외부 연동 중 예외 발생 시, 작업 상태를 실패로 기록합니다.
     * </p>
     */
    @Override
    public Mono<String> initiateGeneration(
            Long userId,
            String startPosition,
            String shape,
            Proficiency proficiency
    ) {
        return geocodePort.geocode(startPosition)
                .switchIfEmpty(Mono.error(new ExternalApiClientException(ExternalApiErrorCode.INVALID_ADDRESS, "해당 주소에 대한 검색 결과가 없습니다.")))
                .zipWhen(geocodeResult -> savePendingTask(userId, shape, proficiency))
                .flatMap(tuple -> {
                    GeocodeResult geocodeResult = tuple.getT1();
                    AiTask pendingTask = tuple.getT2();

                    return processExternalAiServices(pendingTask, geocodeResult, shape, proficiency)
                            .flatMap(this::saveProcessingTask)
                            .onErrorResume(e -> handlePipelineError(pendingTask, e));
                })
                .map(AiTask::id);
    }

    /**
     * AI 작업의 초기 상태(PENDING)를 데이터베이스에 저장합니다.
     * <p>
     * JPA의 블로킹 I/O 작업이므로 {@link Schedulers#boundedElastic()} 스레드 풀로 격리하여 실행합니다.
     * </p>
     *
     * @param userId      요청한 사용자의 ID
     * @param shape       생성할 런닝 아트의 모양
     * @param proficiency 사용자의 러닝 숙련도
     * @return PENDING 상태로 저장된 {@link AiTask} 엔티티를 방출하는 Mono
     */
    private Mono<AiTask> savePendingTask(Long userId, String shape, Proficiency proficiency) {
        return Mono.fromCallable(() -> taskUpdatePort.save(AiTask.createPending(userId, shape, proficiency)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 외부 서비스(S3 및 SageMaker)와 연동하여 AI 생성을 요청하고, 엔티티를 처리 중(PROCESSING) 상태로 변경합니다.
     * <p>
     * S3 업로드 및 SageMaker API 호출과 같은 외부 네트워크 블로킹 작업을 수행하며,
     * 작업 중 예외가 발생하면 {@link #handlePipelineError}로 처리를 위임합니다.
     * </p>
     *
     * @param pendingTask   이전에 PENDING 상태로 저장된 AI 작업 엔티티
     * @param geocodeResult 런닝 시작 위치
     * @param shape         목표 아트 모양
     * @param proficiency   러닝 숙련도
     * @return PROCESSING 상태 및 관련 URI가 업데이트된 {@link AiTask} 객체를 방출하는 Mono
     */
    private Mono<AiTask> processExternalAiServices(
            AiTask pendingTask,
            GeocodeResult geocodeResult,
            String shape,
            Proficiency proficiency
    ) {
        return Mono.fromCallable(() -> {
                    String promptJson = buildPromptJson(geocodeResult, shape, proficiency);
                    String inputS3Uri = s3AiInputPort.uploadInputJson(pendingTask.id(), promptJson);
                    String outputS3Uri = sageMakerAdapter.invokeAsync(inputS3Uri);
                    return pendingTask.markAsProcessing(inputS3Uri, outputS3Uri);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 처리 중(PROCESSING) 상태로 상태가 변경된 AI 작업 정보를 데이터베이스에 반영합니다.
     * <p>
     * JPA 블로킹 I/O 작업이므로 {@link Schedulers#boundedElastic()} 스레드 풀에서 실행됩니다.
     * </p>
     *
     * @param processingTask S3 URI 및 SageMaker 반환값이 포함된 AI 작업 엔티티
     * @return 데이터베이스에 반영된 {@link AiTask} 엔티티를 방출하는 Mono
     */
    private Mono<AiTask> saveProcessingTask(AiTask processingTask) {
        return Mono.fromCallable(() -> taskUpdatePort.save(processingTask))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * AI 파이프라인 처리 중 발생한 예외를 처리하고 작업 상태를 실패(FAILED)로 기록합니다.
     * <p>
     * 에러 로그를 남기고 DB에 실패 상태 및 사유를 반영한 뒤, 클라이언트에게 반환할
     * 최종 비즈니스 예외({@link AiException})를 발생시킵니다.
     * </p>
     *
     * @param aiTask 예외가 발생한 시점의 AI 작업 엔티티
     * @param e      파이프라인에서 발생한 원본 예외
     * @return 에러 파이프라인으로 전환되어 최종적으로 {@link AiException}을 방출하는 Mono
     */
    private Mono<AiTask> handlePipelineError(AiTask aiTask, Throwable e) {
        log.error("[AI Pipeline Error] 요청 처리 중 오류 발생 - TaskID: {}", aiTask.id(), e);

        return Mono.fromCallable(() -> {
                    AiTask failedTask = aiTask.markAsFailed(e.getMessage());
                    return taskUpdatePort.save(failedTask);
                })
                .subscribeOn(Schedulers.boundedElastic())
                // DB 기록마저 실패할 경우 예외를 먹어치우고(Log만 남김) 원래의 비즈니스 예외만 던지도록 처리
                .onErrorResume(dbError -> {
                    log.error("[AI Pipeline Error] 실패 상태 기록 중 DB 2차 오류 발생 - TaskID: {}", aiTask.id(), dbError);
                    return Mono.empty();
                })
                .then(Mono.error(new AiException(AiErrorCode.AI_PIPELINE_ERROR)));
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
     * @param geocodeResult 런닝 시작 위치
     * @param shape         목표 아트 모양
     * @param proficiency   러닝 숙련도
     * @return 직렬화된 JSON 문자열
     * @throws JsonProcessingException 객체를 JSON으로 변환하는 과정에서 오류가 발생할 경우
     */
    private String buildPromptJson(
            GeocodeResult geocodeResult,
            String shape,
            Proficiency proficiency
    ) throws JsonProcessingException {
        Map<String, Object> promptData = Map.of(
                "latitude", geocodeResult.latitude(),
                "longitude", geocodeResult.longitude(),
                "shape", shape != null ? shape : "",
                "proficiency", proficiency != null ? proficiency.name() : Proficiency.BEGINNER.getProficiency()
        );
        return objectMapper.writeValueAsString(promptData);
    }
}