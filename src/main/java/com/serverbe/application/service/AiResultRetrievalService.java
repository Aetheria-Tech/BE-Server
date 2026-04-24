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

        // 1. DB 커넥션을 물고 있지 않은 상태에서 안전하게 S3 네트워크 통신 수행
        s3AiOutputPort.downloadOutput(aiTask.outputS3Uri()).ifPresent(resultDto -> {
            log.info("[Result Retrieval] 결과물 감지 - TaskID: {}", aiTask.id());

            AiTask updatedTask = aiTask;

            try {
                // 2. 이 UseCase 내부의 @Transactional을 타고 짧고 빠르게 DB+Redis 처리
                Long savedArtId = registerCompletedArtUseCase.registerFromPolyline(
                        aiTask.userId(),
                        resultDto.gpx(),
                        aiTask.shape() + " 코스 (" + aiTask.proficiency() + ")",
                        aiTask.shape(),
                        aiTask.proficiency()
                );

                // 3. 상태 갱신
                updatedTask = aiTask.markAsCompleted(savedArtId);

                // 4. SSE 알림 발송 (이것도 네트워크 I/O이므로 트랜잭션 밖에 있는 것이 훨씬 좋습니다!)
                taskNotificationPort.notifyTaskCompleted(updatedTask.id(), updatedTask.outputS3Uri());

                log.info("[Result Retrieval] DB & Redis GEO 등록 성공 - TaskID: {}, ArtID: {}", updatedTask.id(), savedArtId);

            } catch (Exception e) {
                log.error("[Result Retrieval Error] 결과물 등록 중 오류 발생 - TaskID: {}", aiTask.id(), e);

                updatedTask = aiTask.markAsFailed("데이터 등록 실패: " + e.getMessage());
                taskNotificationPort.notifyTaskFailed(updatedTask.id(), "런닝 아트 등록 중 오류가 발생했습니다.");

            } finally {
                // 5. 어댑터 자체 트랜잭션을 타고 안전하게 최종 상태 저장
                taskUpdatePort.save(updatedTask);
            }
        });
    }
}