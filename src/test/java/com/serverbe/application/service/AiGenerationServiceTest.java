package com.serverbe.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serverbe.application.port.in.dto.task.TaskStatusResult;
import com.serverbe.application.port.out.dto.geocoding.GeocodeResult;
import com.serverbe.application.port.out.geocode.GeocodePort;
import com.serverbe.application.port.out.s3.S3AiInputPort;
import com.serverbe.application.port.out.sagemaker.SageMakerAsyncPort;
import com.serverbe.application.port.out.task.TaskQueryPort;
import com.serverbe.application.port.out.task.TaskRateLimitPort;
import com.serverbe.application.port.out.task.TaskUpdatePort;
import com.serverbe.domain.exception.ai.AiErrorCode;
import com.serverbe.domain.exception.ai.AiException;
import com.serverbe.domain.exception.external.ExternalApiClientException;
import com.serverbe.domain.exception.external.ExternalApiErrorCode;
import com.serverbe.domain.exception.s3.S3ErrorCode;
import com.serverbe.domain.exception.s3.S3Exception;
import com.serverbe.domain.model.art.vo.Proficiency;
import com.serverbe.domain.model.task.AiTask;
import com.serverbe.domain.model.task.vo.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link AiGenerationService}는 {@link com.serverbe.application.port.in.art.InitiateAiGenerationUseCase}와
 * {@link com.serverbe.application.port.in.task.GetTaskStatusUseCase}를 구현하는 요청 진입점이므로,
 * 두 UseCase 메서드 각각에 대해 성공 케이스와 대표적인 실패 케이스를 모두 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class AiGenerationServiceTest {

    @Mock
    private TaskQueryPort taskQueryPort;
    @Mock
    private TaskUpdatePort taskUpdatePort;
    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Mock
    private GeocodePort geocodePort;
    @Mock
    private TaskRateLimitPort taskRateLimitPort;
    @Mock
    private S3AiInputPort s3AiInputPort;
    @Mock
    private SageMakerAsyncPort sageMakerAsyncPort;
    @Mock
    private com.serverbe.application.service.helper.AiTaskResourceCleaner resourceCleaner;

    private AiGenerationService aiGenerationService;

    private static final Long USER_ID = 1L;
    private static final String GENERATED_TASK_ID = "task-abc-123";

    @BeforeEach
    void setUp() {
        // ObjectMapper는 순수 직렬화 유틸이므로 mocking 없이 실제 인스턴스를 사용합니다.
        // 상태 전이 구간은 TransactionTemplate 으로 감싸져 있습니다.
        // 단위 테스트에서는 트랜잭션 매니저를 목으로 두어 콜백이 그대로 실행되게만 합니다.
        aiGenerationService = new AiGenerationService(
                taskQueryPort, taskUpdatePort, geocodePort, taskRateLimitPort, s3AiInputPort, sageMakerAsyncPort,
                resourceCleaner, new ObjectMapper(), new TransactionTemplate(transactionManager)
        );
    }

    /**
     * DB가 신규 저장(PENDING) 시 ID를 채번해주는 것을 흉내내는 스텁.
     * 이미 ID가 있는 도메인 객체(PROCESSING/FAILED 갱신)는 그대로 통과시킵니다.
     */
    private void stubSaveAssignsIdOnce() {
        given(taskUpdatePort.save(any(AiTask.class))).willAnswer(invocation -> {
            AiTask task = invocation.getArgument(0);
            if (task.id() == null) {
                return new AiTask(GENERATED_TASK_ID, task.userId(), task.shape(), task.proficiency(), task.status(),
                        task.inputS3Uri(), task.outputS3Uri(), task.errorMessage(), task.createdAt(), task.updatedAt(), task.resultArtId());
            }
            return task;
        });
    }

    // ================= initiateGeneration =================

    @Test
    @DisplayName("성공: 레이트리밋/중복작업 검증을 통과하고 지오코딩~S3~SageMaker 파이프라인이 정상 처리되면 Task ID를 반환한다")
    void initiateGeneration_Success() {
        // given
        given(taskRateLimitPort.tryLock(USER_ID, 5)).willReturn(true);
        given(taskQueryPort.existsActiveTaskByUserId(USER_ID)).willReturn(false);
        given(geocodePort.geocode(anyString())).willReturn(Mono.just(new GeocodeResult(37.5, 127.0, "서울시 강남구")));
        stubSaveAssignsIdOnce();
        given(s3AiInputPort.uploadInputJson(anyString(), anyString())).willReturn("s3://bucket/inputs/" + GENERATED_TASK_ID + ".json");
        given(sageMakerAsyncPort.invokeAsync(anyString(), anyString())).willReturn("s3://bucket/outputs/" + GENERATED_TASK_ID + ".json.out");

        // when
        Mono<String> resultMono = aiGenerationService.initiateGeneration(USER_ID, "서울시 강남구", "HEART", Proficiency.BEGINNER);

        // then
        StepVerifier.create(resultMono)
                .expectNext(GENERATED_TASK_ID)
                .verifyComplete();

        verify(taskUpdatePort, times(2)).save(any(AiTask.class)); // PENDING 저장 -> PROCESSING 저장
        verify(s3AiInputPort, never()).deleteInputFile(anyString()); // 보상 트랜잭션은 발동되지 않아야 함
    }

    @Test
    @DisplayName("실패: Redis 기반 연타 방지(SETNX)에 걸리면 RATE_LIMIT_EXCEEDED 예외가 발생하고 이후 단계는 실행되지 않는다")
    void initiateGeneration_Fail_RateLimited() {
        // given
        given(taskRateLimitPort.tryLock(USER_ID, 5)).willReturn(false);
        // Reactor 체인은 구독 여부와 무관하게 `.then(Mono)`의 인자 Mono를 즉시(메서드 호출 시점에) 평가하므로,
        // 실제로 구독되어 실행되지는 않더라도 NPE를 피하려면 non-null Mono를 반환하도록 스텁이 필요하다.
        given(geocodePort.geocode(anyString())).willReturn(Mono.just(new GeocodeResult(37.5, 127.0, "안 쓰일 더미 주소")));

        // when & then
        StepVerifier.create(aiGenerationService.initiateGeneration(USER_ID, "서울시 강남구", "HEART", Proficiency.BEGINNER))
                .expectErrorMatches(e -> e instanceof AiException ae && ae.getErrorCode() == AiErrorCode.RATE_LIMIT_EXCEEDED)
                .verify();

        // 참고: Reactor 체인 구성 시 `.then(geocodePort.geocode(...))`의 인자 Mono는 메서드 호출 시점에
        // 즉시 평가되므로 geocodePort.geocode()는 호출은 되지만, 레이트리밋 단계에서 이미 에러가 나
        // 그 Mono는 실제로 구독(subscribe)되지 않는다. 따라서 DB 중복 검증과 실제 저장은 일어나지 않아야 한다.
        verify(taskQueryPort, never()).existsActiveTaskByUserId(anyLong());
        verify(taskUpdatePort, never()).save(any());
    }

    @Test
    @DisplayName("실패: 이미 진행 중인(PENDING/PROCESSING) 작업이 있으면 DUPLICATE_AI_REQUEST 예외가 발생한다")
    void initiateGeneration_Fail_DuplicateActiveTask() {
        // given
        given(taskRateLimitPort.tryLock(USER_ID, 5)).willReturn(true);
        given(taskQueryPort.existsActiveTaskByUserId(USER_ID)).willReturn(true);
        // 위와 동일한 이유로 non-null Mono 스텁이 필요하다 (실제로 구독되지는 않음).
        given(geocodePort.geocode(anyString())).willReturn(Mono.just(new GeocodeResult(37.5, 127.0, "안 쓰일 더미 주소")));

        // when & then
        StepVerifier.create(aiGenerationService.initiateGeneration(USER_ID, "서울시 강남구", "HEART", Proficiency.BEGINNER))
                .expectErrorMatches(e -> e instanceof AiException ae && ae.getErrorCode() == AiErrorCode.DUPLICATE_AI_REQUEST)
                .verify();

        // DB 중복 검증 단계에서 이미 에러가 나므로, 지오코딩 결과 Mono는 구독되지 않아 PENDING Task 저장도 없어야 한다.
        verify(taskUpdatePort, never()).save(any());
    }

    @Test
    @DisplayName("실패: 지오코딩 결과가 비어있으면(switchIfEmpty) INVALID_ADDRESS 예외가 발생하고 PENDING Task는 생성되지 않는다")
    void initiateGeneration_Fail_GeocodeEmptyResult() {
        // given
        given(taskRateLimitPort.tryLock(USER_ID, 5)).willReturn(true);
        given(taskQueryPort.existsActiveTaskByUserId(USER_ID)).willReturn(false);
        given(geocodePort.geocode(anyString())).willReturn(Mono.empty());

        // when & then
        StepVerifier.create(aiGenerationService.initiateGeneration(USER_ID, "존재하지않는주소", "HEART", Proficiency.BEGINNER))
                .expectErrorMatches(e -> e instanceof ExternalApiClientException ex
                        && ex.getErrorCode() == ExternalApiErrorCode.INVALID_ADDRESS)
                .verify();

        // 지오코딩 단계에서 실패했으므로 PENDING Task 저장 자체가 시도되지 않아야 한다
        verify(taskUpdatePort, never()).save(any());
    }

    @Test
    @DisplayName("실패: PENDING 저장 이후 S3 업로드가 실패하면 Task가 FAILED로 기록되고 AI_PIPELINE_ERROR 예외가 발생한다")
    void initiateGeneration_Fail_S3UploadError_MarksTaskFailed() {
        // given
        given(taskRateLimitPort.tryLock(USER_ID, 5)).willReturn(true);
        given(taskQueryPort.existsActiveTaskByUserId(USER_ID)).willReturn(false);
        given(geocodePort.geocode(anyString())).willReturn(Mono.just(new GeocodeResult(37.5, 127.0, "서울시 강남구")));
        stubSaveAssignsIdOnce();
        given(s3AiInputPort.uploadInputJson(anyString(), anyString()))
                .willThrow(new S3Exception(S3ErrorCode.S3_UPLOAD_ERROR, "S3 업로드 실패"));

        // when & then
        StepVerifier.create(aiGenerationService.initiateGeneration(USER_ID, "서울시 강남구", "HEART", Proficiency.BEGINNER))
                .expectErrorMatches(e -> e instanceof AiException ae && ae.getErrorCode() == AiErrorCode.AI_PIPELINE_ERROR)
                .verify();

        ArgumentCaptor<AiTask> captor = ArgumentCaptor.forClass(AiTask.class);
        verify(taskUpdatePort, times(2)).save(captor.capture()); // 1) PENDING 저장, 2) FAILED 저장
        assertThat(captor.getAllValues().get(1).status()).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    @DisplayName("실패: SageMaker 호출이 실패하면 이미 올린 S3 입력 파일을 삭제하는 보상 트랜잭션이 동작하고 Task는 FAILED로 기록된다")
    void initiateGeneration_Fail_SageMakerError_TriggersS3Compensation() {
        // given
        String inputS3Uri = "s3://bucket/inputs/" + GENERATED_TASK_ID + ".json";
        given(taskRateLimitPort.tryLock(USER_ID, 5)).willReturn(true);
        given(taskQueryPort.existsActiveTaskByUserId(USER_ID)).willReturn(false);
        given(geocodePort.geocode(anyString())).willReturn(Mono.just(new GeocodeResult(37.5, 127.0, "서울시 강남구")));
        stubSaveAssignsIdOnce();
        given(s3AiInputPort.uploadInputJson(anyString(), anyString())).willReturn(inputS3Uri);
        given(sageMakerAsyncPort.invokeAsync(GENERATED_TASK_ID, inputS3Uri))
                .willThrow(new RuntimeException("SageMaker 엔드포인트 응답 없음"));

        // when & then
        StepVerifier.create(aiGenerationService.initiateGeneration(USER_ID, "서울시 강남구", "HEART", Proficiency.BEGINNER))
                .expectErrorMatches(e -> e instanceof AiException ae && ae.getErrorCode() == AiErrorCode.AI_PIPELINE_ERROR)
                .verify();

        // 보상 트랜잭션: SageMaker 실패 시 이미 업로드된 S3 입력 파일을 삭제해야 한다
        verify(s3AiInputPort).deleteInputFile(inputS3Uri);

        ArgumentCaptor<AiTask> captor = ArgumentCaptor.forClass(AiTask.class);
        verify(taskUpdatePort, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).status()).isEqualTo(TaskStatus.FAILED);
    }

    // ================= getTaskStatus =================

    @Test
    @DisplayName("성공: 본인의 Task를 조회하면 상태 정보를 반환한다")
    void getTaskStatus_Success() {
        // given
        AiTask task = AiTask.createPending(USER_ID, "HEART", Proficiency.BEGINNER);
        given(taskQueryPort.findById(GENERATED_TASK_ID)).willReturn(Optional.of(task));

        // when
        TaskStatusResult response = aiGenerationService.getTaskStatus(GENERATED_TASK_ID, USER_ID);

        // then
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(TaskStatus.PENDING);
    }

    @Test
    @DisplayName("실패: 존재하지 않는 Task ID로 조회하면 NOT_FOUND_AITASK 예외가 발생한다")
    void getTaskStatus_Fail_NotFound() {
        // given
        given(taskQueryPort.findById(GENERATED_TASK_ID)).willReturn(Optional.empty());

        // when & then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> aiGenerationService.getTaskStatus(GENERATED_TASK_ID, USER_ID))
                .isInstanceOf(AiException.class)
                .hasFieldOrPropertyWithValue("errorCode", AiErrorCode.NOT_FOUND_AITASK);
    }

    @Test
    @DisplayName("실패: 타인의 Task를 조회하려고 하면 USER_IS_NOT_OWNER_OF_TASK 예외가 발생한다")
    void getTaskStatus_Fail_NotOwner() {
        // given
        Long ownerId = USER_ID;
        Long strangerId = 999L;
        AiTask task = AiTask.createPending(ownerId, "HEART", Proficiency.BEGINNER);
        given(taskQueryPort.findById(GENERATED_TASK_ID)).willReturn(Optional.of(task));

        // when & then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> aiGenerationService.getTaskStatus(GENERATED_TASK_ID, strangerId))
                .isInstanceOf(AiException.class)
                .hasFieldOrPropertyWithValue("errorCode", AiErrorCode.USER_IS_NOT_OWNER_OF_TASK);
    }
}
