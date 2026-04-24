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

/**
 * AI 작업(Task)의 결과물을 조회하고 후속 처리를 담당하는 비즈니스 서비스 구현체.
 * <p>
 * <b>트랜잭션 경계 최적화 (Transaction Boundary Optimization):</b><br>
 * 이 클래스는 고의적으로 메서드 레벨의 {@code @Transactional}을 사용하지 않습니다.
 * S3 다운로드나 SSE 알림 발송과 같은 '외부 네트워크 I/O' 작업 시 DB 커넥션을 점유하지 않도록 하여,
 * 서버의 DB 커넥션 풀(Connection Pool) 고갈을 방지하고 시스템 전체의 처리량을 극대화합니다.
 * 실제 DB 저장이 필요한 로직은 내부의 분리된 UseCase나 Adapter가 제공하는 짧은 트랜잭션을 활용합니다.
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

    /**
     * 처리 중(PROCESSING)인 AI 작업의 결과물을 S3에서 확인하고,
     * 결과물이 존재할 경우 DB 및 Redis에 등록한 뒤 클라이언트에게 완료 알림을 발송합니다.
     * <p>
     * <b>처리 파이프라인:</b><br>
     * 1. <b>S3 조회 (Non-Transactional):</b> DB 커넥션 없이 안전하게 S3에서 결과 파일(JSON)을 조회합니다.<br>
     * 2. <b>데이터 등록 (Transactional):</b> 결과물이 있다면 {@link RegisterCompletedArtUseCase}를 호출하여 짧고 빠르게 DB와 Redis GEO 데이터를 저장합니다.<br>
     * 3. <b>상태 변경 및 알림 (Non-Transactional):</b> 작업 상태를 성공/실패로 갱신하고 클라이언트에게 SSE 이벤트를 발송합니다.<br>
     * 4. <b>최종 상태 저장:</b> 모든 과정이 끝난 후 변경된 Task 상태를 DB에 최종 반영합니다.
     * </p>
     *
     * @param aiTask 현재 상태를 확인할 대상 AI 작업 엔티티
     */
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
                taskNotificationPort.notifyTaskCompleted(
                        updatedTask.id(),
                        String.valueOf(savedArtId)
                );

                log.info("[Result Retrieval] DB & Redis GEO 등록 성공 - TaskID: {}, ArtID: {}", updatedTask.id(), savedArtId);

            } catch (Exception e) {
                log.error("[Result Retrieval Error] 결과물 등록 중 오류 발생 - TaskID: {}", aiTask.id(), e);

                // 롤백이 필요할 정도의 크리티컬한 에러 발생 시 상태를 FAILED로 변경하고 실패 알림 전송
                updatedTask = aiTask.markAsFailed("데이터 등록 실패: " + e.getMessage());
                taskNotificationPort.notifyTaskFailed(updatedTask.id(), "런닝 아트 등록 중 오류가 발생했습니다.");

            } finally {
                // 5. 어댑터 자체 트랜잭션을 타고 안전하게 최종 상태 저장
                taskUpdatePort.save(updatedTask);
            }
        });
    }
}