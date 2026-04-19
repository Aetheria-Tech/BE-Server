package com.serverbe.application.port.in.task;

public interface UpdateTaskStatusUseCase {
    /**
     * AI 작업을 성공(COMPLETED) 상태로 변경하고 클라이언트에게 알림을 보냅니다.
     */
    void completeTask(String taskId, String resultS3Uri);

    /**
     * AI 작업을 실패(FAILED) 상태로 변경하고 클라이언트에게 알림을 보냅니다.
     */
    void failTask(String taskId, String errorMessage);
}