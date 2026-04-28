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
        // 1. S3에서 결과물 다운로드 (트랜잭션 밖에서 실행)
        s3AiOutputPort.downloadOutput(aiTask.outputS3Uri()).ifPresent(resultDto -> {
            log.info("[Result Retrieval] 결과물 감지 - TaskID: {}", aiTask.id());

            try {
                // 2. 성공 비즈니스 로직 처리
                handleSuccess(aiTask, resultDto);
            } catch (Exception e) {
                // 3. 실패 시 롤백 및 에러 처리 로직
                handleFailure(aiTask, e);
            }
        });
    }

    /**
     * S3 다운로드 성공 시 실행되는 파이프라인.
     * DB 저장 -> S3 임시파일 삭제 -> 클라이언트 알림 순서로 안전하게 실행됩니다.
     */
    private void handleSuccess(AiTask aiTask, AiGenerationResultDto resultDto) {
        // 1. [Transactional] DB & Redis에 런닝 아트 등록
        Long savedArtId = registerCompletedArtUseCase.registerFromPolyline(
                aiTask.userId(),
                resultDto.gpx(),
                aiTask.shape() + " 코스 (" + aiTask.proficiency().getProficiency() + ")",
                aiTask.shape(),
                aiTask.proficiency()
        );

        // 2. [Transactional] Task 상태를 DB에 최종 반영
        AiTask updatedTask = aiTask.markAsCompleted(savedArtId);
        taskUpdatePort.save(updatedTask);
        log.info("[Result Retrieval] Task 최종 상태 DB 저장 완료 - TaskID: {}", updatedTask.id());

        // 3. [비용 최적화] 사용 완료된 S3 입력/출력 데이터 삭제
        // s3AiOutputPort.deleteOutput(aiTask.outputS3Uri());

        // 4. [네트워크 I/O] DB 저장이 완벽히 끝난 후 알림 발송 (Resource ID 전송)
        taskNotificationPort.notifyTaskCompleted(updatedTask.id(), String.valueOf(savedArtId));
        log.info("[Result Retrieval] 파이프라인 완수 및 알림 발송 성공 - TaskID: {}, ArtID: {}", updatedTask.id(), savedArtId);
    }

    /**
     * 처리 중 에러 발생 시 상태를 복구하고 실패 알림을 전송하는 파이프라인.
     */
    private void handleFailure(AiTask aiTask, Exception e) {
        log.error("[Result Retrieval Error] 결과물 등록 중 오류 발생 - TaskID: {}", aiTask.id(), e);

        // 1. [Transactional] DB에 실패 상태 먼저 기록
        AiTask failedTask = aiTask.markAsFailed("데이터 등록 실패: " + e.getMessage());
        taskUpdatePort.save(failedTask);

        // 2. [네트워크 I/O] 클라이언트에 실패 알림 전송
        taskNotificationPort.notifyTaskFailed(failedTask.id(), "런닝 아트 등록 중 오류가 발생했습니다.");
    }
}