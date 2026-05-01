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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AI 작업(Task)의 결과물을 조회하고 후속 처리를 담당하는 비즈니스 서비스 구현체.
 * <p>
 * 외부 네트워크 I/O(S3 연동, SSE 알림) 작업 시 DB 커넥션 풀 고갈을 방지하기 위해
 * 의도적으로 선언적 트랜잭션(@Transactional)을 배제하고,
 * TransactionTemplate을 사용하여 DB 등록 과정만 프로그래밍 방식으로 원자성을 보장합니다.
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
    private final TransactionTemplate transactionTemplate;

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
     * 트랜잭션 내에서 처리할 반환값을 묶기 위한 레코드
     */
    private record ProcessResult(Long savedArtId, AiTask updatedTask) {
    }

    /**
     * 성공 파이프라인: 데이터 등록 -> 상태 갱신 -> 임시 파일 삭제 -> 클라이언트 알림
     */
    private void handleSuccess(AiTask aiTask, AiGenerationResultDto resultDto) {

        // 1. 핵심 비즈니스 로직 (TransactionTemplate으로 원자성 보장)
        // 여기서 에러가 나면 saveArt와 updateTask가 함께 롤백되고 Exception이 던져집니다.
        ProcessResult processResult = transactionTemplate.execute(status -> {
            Long artId = saveArt(aiTask, resultDto);
            AiTask task = updateTask(aiTask, artId);
            return new ProcessResult(artId, task);
        });

        log.info("[Result Retrieval] Task 최종 상태 DB 저장 완료 - TaskID: {}", processResult.updatedTask().id());

        // 2. 부가 로직 (외부 I/O이므로 트랜잭션 블록 밖에서 안전하게 실행)
        deleteDataFromS3(aiTask);
        sendCompletionNotification(processResult.updatedTask(), processResult.savedArtId());
    }

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
        s3AiOutputPort.deleteOutput(aiTask.outputS3Uri());
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