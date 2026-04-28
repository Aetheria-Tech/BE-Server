package com.serverbe.application.service;

import com.serverbe.application.port.in.art.RegisterCompletedArtUseCase;
import com.serverbe.application.port.in.dto.art.AiGenerationResultDto;
import com.serverbe.application.port.in.task.RetrieveAiResultUseCase;
import com.serverbe.application.port.out.notification.TaskNotificationPort;
import com.serverbe.application.port.out.s3.S3AiOutputPort;
import com.serverbe.application.port.out.task.TaskUpdatePort;
import com.serverbe.domain.model.task.AiTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 작업(Task)의 결과물을 조회하고 후속 처리를 담당하는 비즈니스 서비스 구현체.
 * <p>
 * 외부 네트워크 I/O(S3 연동, SSE 알림) 작업 시 DB 커넥션 풀 고갈을 방지하기 위해
 * 의도적으로 클래스 레벨의 @Transactional을 배제하고,
 * 내부 UseCase 및 Port 호출 시에만 짧은 트랜잭션이 유지되도록 설계되었습니다.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiResultRetrievalService implements RetrieveAiResultUseCase {

    private final TaskUpdatePort taskUpdatePort;
    private final S3AiOutputPort s3AiOutputPort;
    private final TaskNotificationPort taskNotificationPort;
    private final RegisterCompletedArtUseCase registerCompletedArtUseCase;

    @Override
    public void processTaskResult(AiTask aiTask) {
        s3AiOutputPort.downloadOutput(aiTask.outputS3Uri()).ifPresent(resultDto -> {
            log.info("[Result Retrieval] 결과물 감지 - TaskID: {}", aiTask.id());

            try {
                handleSuccess(aiTask, resultDto);
            } catch (Exception e) {
                handleFailure(aiTask, e);
            }
        });
    }

    /**
     * 성공 파이프라인: 데이터 등록 -> 상태 갱신 -> 임시 파일 삭제 -> 클라이언트 알림
     */
    private void handleSuccess(AiTask aiTask, AiGenerationResultDto resultDto) {
        // 1. 핵심 비즈니스 로직 (실패 시 Exception을 던져 handleFailure로 롤백)
        Long savedArtId = saveArt(aiTask, resultDto);
        AiTask updatedTask = updateTask(aiTask, savedArtId);
        log.info("[Result Retrieval] Task 최종 상태 DB 저장 완료 - TaskID: {}", updatedTask.id());

        // 2. 부가 로직 (실패하더라도 메인 비즈니스에 영향을 주지 않도록 내부 격리됨)
        deleteDataFromS3(aiTask);
        sendCompletionNotification(updatedTask, savedArtId);
    }

    /**
     * 실패 파이프라인: Task 상태 복구(FAILED) 및 클라이언트 에러 알림 발송
     */
    private void handleFailure(AiTask aiTask, Exception e) {
        log.error("[Result Retrieval Error] 결과물 등록 중 오류 발생 - TaskID: {}", aiTask.id(), e);

        AiTask failedTask = aiTask.markAsFailed("데이터 등록 실패: " + e.getMessage());
        taskUpdatePort.save(failedTask);

        taskNotificationPort.notifyTaskFailed(failedTask.id(), "런닝 아트 등록 중 오류가 발생했습니다.");
    }

    private Long saveArt(AiTask aiTask, AiGenerationResultDto resultDto) {
        return registerCompletedArtUseCase.registerFromPolyline(
                aiTask.userId(),
                resultDto.gpx(),
                aiTask.shape() + " 코스 (" + aiTask.proficiency().getProficiency() + ")",
                aiTask.shape(),
                aiTask.proficiency()
        );
    }

    private AiTask updateTask(AiTask aiTask, Long savedArtId) {
        AiTask updatedTask = aiTask.markAsCompleted(savedArtId);
        taskUpdatePort.save(updatedTask);
        return updatedTask;
    }

    private void deleteDataFromS3(AiTask aiTask) {
        try {
            s3AiOutputPort.deleteOutput(aiTask.outputS3Uri());
        } catch (Exception e) {
            log.warn("[Result Retrieval Warning] S3 임시 파일 삭제 실패 (1일 후 자동 삭제됨) - TaskID: {}", aiTask.id(), e);
        }
    }

    private void sendCompletionNotification(AiTask updatedTask, Long savedArtId) {
        try {
            taskNotificationPort.notifyTaskCompleted(updatedTask.id(), String.valueOf(savedArtId));
            log.info("[Result Retrieval] 파이프라인 완수 및 알림 발송 성공 - TaskID: {}, ArtID: {}", updatedTask.id(), savedArtId);
        } catch (Exception e) {
            log.error("[Result Retrieval Error] DB 저장은 완료되었으나 SSE 알림 발송에 실패했습니다. - TaskID: {}", updatedTask.id(), e);
        }
    }
}