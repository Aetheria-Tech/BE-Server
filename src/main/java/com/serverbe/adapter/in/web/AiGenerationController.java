package com.serverbe.adapter.in.web;

import com.serverbe.adapter.in.web.dto.task.TaskStatusResponse;
import com.serverbe.application.service.AiGenerationService;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/running-arts/tasks")
public class AiGenerationController {

    private final AiGenerationService aiGenerationService;

    /**
     * @param taskId 생성 시 발급받은 Task ID
     * @param userId 검증용 userID
     * @return 작업 상태 정보 (HTTP 200 OK)
     * @responsibility 프론트엔드가 1~3초 주기로 호출하여 AI 비동기 작업의 진행 상황을 폴링(Polling)합니다.
     */
    @GetMapping("/{taskId}")
    public ResponseEntity<TaskStatusResponse> checkTaskStatus(
            @PathVariable("taskId") String taskId,
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        TaskStatusResponse response = aiGenerationService.getTaskStatus(taskId, userId);
        return ResponseEntity.ok(response);
    }
}