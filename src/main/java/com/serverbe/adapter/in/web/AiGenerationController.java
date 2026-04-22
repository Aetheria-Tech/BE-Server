package com.serverbe.adapter.in.web;

import com.serverbe.adapter.in.web.dto.art.CreateRunningArtRequest;
import com.serverbe.adapter.in.web.dto.task.TaskStatusResponse;
import com.serverbe.application.port.in.art.InitiateAiGenerationUseCase;
import com.serverbe.application.port.in.task.GetTaskStatusUseCase;
import com.serverbe.infrastructure.common.response.RestApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/running-arts/tasks")
public class AiGenerationController {

    private final GetTaskStatusUseCase getTaskStatusUseCase;
    private final InitiateAiGenerationUseCase initiateAiGenerationUseCase;

    /**
     * @param taskId 생성 시 발급받은 Task ID
     * @param userId 검증용 userID
     * @return 작업 상태 정보 (HTTP 200 OK)
     * @responsibility 프론트엔드가 1~3초 주기로 호출하여 AI 비동기 작업의 진행 상황을 폴링(Polling)합니다.
     */
    @GetMapping("/{taskId}")
    public RestApiResponse<TaskStatusResponse> checkTaskStatus(
            @PathVariable("taskId") String taskId,
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        TaskStatusResponse response = getTaskStatusUseCase.getTaskStatus(taskId, userId);
        return RestApiResponse.success(response);
    }

    @PostMapping
    public RestApiResponse<String> initiateGeneration(
            @RequestBody CreateRunningArtRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        // Service의 생성 로직 호출! DB에 PENDING 상태로 저장되고 Task ID가 반환됨
        String taskId = initiateAiGenerationUseCase.initiateGeneration(
                userId,
                request.startPosition(),
                request.shape(),
                request.proficiency()
        );

        // 생성된 taskId 반환 (HTTP 201 CREATED)
        return RestApiResponse.success(taskId);
    }
}