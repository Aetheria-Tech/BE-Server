package com.serverbe.domain.model.art.task;

/**
 * 비동기 AI 이미지/폴리라인 생성 작업의 진행 상태
 */
public enum TaskStatus {
    PENDING,    // 1. 대기열 등록됨 (S3 업로드 전/중)
    PROCESSING, // 2. SageMaker에서 AI 추론 진행 중
    COMPLETED,  // 3. 작업 성공 (S3에서 결과물 다운로드 완료)
    FAILED      // 4. 작업 실패 (에러 발생)
}