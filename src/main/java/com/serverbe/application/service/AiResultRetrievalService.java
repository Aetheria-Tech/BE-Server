package com.serverbe.application.service;

import com.serverbe.application.port.in.art.RegisterCompletedArtUseCase;
import com.serverbe.application.port.in.task.RetrieveAiResultUseCase;
import com.serverbe.application.port.out.notification.TaskNotificationPort;
import com.serverbe.application.port.out.s3.S3AiOutputPort;
import com.serverbe.application.port.out.task.TaskUpdatePort;
import com.serverbe.domain.model.task.AiTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiResultRetrievalService implements RetrieveAiResultUseCase {

    private final TaskUpdatePort taskUpdatePort;
    private final S3AiOutputPort s3AiOutputPort;
    private final TaskNotificationPort taskNotificationPort;
    private final RegisterCompletedArtUseCase registerCompletedArtUseCase;

    @Override
    @Transactional
    public void processTaskResult(AiTask aiTask) {
        s3AiOutputPort.downloadOutput(aiTask.outputS3Uri()).ifPresent(resultDto -> {
            log.info("[Result Retrieval] 결과물 감지 - TaskID: {}", aiTask.id());

            AiTask updatedTask = aiTask;

            try {
                // 1. UseCase를 호출하여 [DB 저장 + Redis GEO 등록]을 한 번에 처리합니다.
                Long savedArtId = registerCompletedArtUseCase.registerFromPolyline(
                        aiTask.userId(),
                        resultDto.gpx(),
                        aiTask.shape() + " 코스 (" + aiTask.proficiency() + ")",
                        aiTask.shape(),
                        aiTask.proficiency()
                );

                // 2. Record 불변성 준수: 완료 상태의 새 도메인 객체 생성
                updatedTask = aiTask.markAsCompleted(savedArtId);

                // 3. 성공 알림 발송 (SSE)
                taskNotificationPort.notifyTaskCompleted(updatedTask.id(), updatedTask.outputS3Uri());

                log.info("[Result Retrieval] DB & Redis GEO 등록 성공 - TaskID: {}, ArtID: {}", updatedTask.id(), savedArtId);

            } catch (Exception e) {
                log.error("[Result Retrieval Error] 결과물 등록 중 오류 발생 - TaskID: {}", aiTask.id(), e);

                // 실패 상태로 변경
                updatedTask = aiTask.markAsFailed("데이터 등록 실패: " + e.getMessage());

                // 실패 알림 발송 (SSE)
                taskNotificationPort.notifyTaskFailed(updatedTask.id(), "런닝 아트 등록 중 오류가 발생했습니다.");
            } finally {
                // 4. 최종 상태(성공 or 실패)를 Port를 통해 영구 저장
                taskUpdatePort.save(updatedTask);
            }
        });
    }
}