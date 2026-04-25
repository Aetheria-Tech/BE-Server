package com.serverbe.application.port.in.task;

import com.serverbe.domain.model.task.AiTask;

/**
 * 완료된 AI 작업의 결과물을 가져와 후속 처리를 수행하는 유스케이스 (Inbound Port).
 * <p>
 * 비동기 AI 워커(SageMaker 등)의 처리가 완료되었을 때,
 * 결과 파일(S3)을 읽어와 RDB 및 Redis에 최종 반영하고 클라이언트에게 알림을 보내는
 * 일련의 파이프라인을 실행합니다.
 * </p>
 */
public interface RetrieveAiResultUseCase {

    /**
     * AI 작업 엔티티를 기반으로 외부 스토리지에서 결과물을 조회하고, 성공/실패에 따른 최종 상태를 처리합니다.
     *
     * @param aiTask 상태를 확인하고 후속 로직을 적용할 대상 AI 작업 도메인 객체
     */
    void processTaskResult(AiTask aiTask);
}