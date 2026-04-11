package com.serverbe.application.service;

import com.serverbe.adapter.in.web.dto.task.TaskStatusResponse;
import com.serverbe.adapter.out.persistence.task.AiTaskEntity;
import com.serverbe.adapter.out.persistence.task.JpaAiTaskRepository;
import com.serverbe.infrastructure.aws.S3AiInputAdapter;
import com.serverbe.infrastructure.aws.SageMakerAsyncAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 생성 비동기 파이프라인을 총괄하는 서비스 (Facade)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiGenerationService {

    private final JpaAiTaskRepository taskRepository;
    private final S3AiInputAdapter s3Adapter;
    private final SageMakerAsyncAdapter sageMakerAdapter;

    /**
     * 사용자의 AI 생성 요청을 비동기 파이프라인에 등록합니다.
     *
     * @param userId      요청 사용자 ID
     * @param promptJson  AI 모델에 전달할 프롬프트 및 파라미터 JSON
     * @return 발급된 고유 Task ID (UUID)
     * @responsibility 1. DB에 PENDING 상태로 Task를 생성합니다.
     * 2. S3에 입력 데이터를 업로드하고 URI를 획득합니다.
     * 3. SageMaker 비동기 엔드포인트를 호출합니다.
     * 4. 성공 시 상태를 PROCESSING으로 변경하고 최종 Task ID를 반환합니다.
     */
    @Transactional
    public String initiateGeneration(Long userId, String promptJson) {
        // 1. 대기 상태의 Task 레코드 생성 (ID 발급 목적)
        AiTaskEntity task = AiTaskEntity.builder()
                .userId(userId)
                .build();
        AiTaskEntity savedTask = taskRepository.save(task);
        String taskId = savedTask.getId();

        try {
            log.info("[AI Pipeline] 비동기 요청 시작 - TaskID: {}, UserID: {}", taskId, userId);

            // 2. S3에 요청 JSON 업로드
            String inputS3Uri = s3Adapter.uploadInputJson(taskId, promptJson);

            // 3. SageMaker 비동기 엔드포인트 호출 (Trigger)
            // 호출 시 예상되는 Output 경로를 리턴받습니다.
            String outputS3Uri = sageMakerAdapter.invokeAsync(inputS3Uri);

            // 4. Task 정보 업데이트 (상태: PROCESSING)
            savedTask.markAsProcessing(inputS3Uri, outputS3Uri);
            log.info("[AI Pipeline] 비동기 요청 등록 완료 - TaskID: {}", taskId);

            return taskId;

        } catch (Exception e) {
            log.error("[AI Pipeline Error] 요청 처리 중 치명적 오류 - TaskID: {}, 원인: {}", taskId, e.getMessage());

            // 실패 상태 기록 (트랜잭션 내에서 상태 변경)
            savedTask.markAsFailed(e.getMessage());

            // 필요에 따라 사용자 정의 예외로 래핑하여 던짐
            throw new RuntimeException("AI 생성 요청을 처리할 수 없습니다.", e);
        }
    }

    /**
     * @param taskId 조회할 작업의 고유 ID
     * @param userId 요청자의 고유 ID (보안용)
     * @return 현재 작업 상태를 담은 DTO
     * @responsibility 클라이언트의 폴링 요청에 대해 Task DB를 조회하고 현재 상태를 반환합니다.
     */
    @Transactional(readOnly = true) // 성능 최적화를 위해 읽기 전용 트랜잭션 적용
    public TaskStatusResponse getTaskStatus(String taskId, Long userId) {

        // 1. Task 조회 및 소유권 검증 (이전에 Repository에 만들어둔 메서드 활용!)
        AiTaskEntity task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 작업을 찾을 수 없거나 접근 권한이 없습니다."));

        // 2. DTO로 변환하여 반환
        return TaskStatusResponse.from(task);
    }
}