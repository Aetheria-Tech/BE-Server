package com.serverbe.application.port.in.task;

import com.serverbe.domain.model.task.AiTask;

public interface RetrieveAiResultUseCase {
    void processTaskResult(AiTask aiTask);
}