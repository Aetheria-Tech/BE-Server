package com.serverbe.adapter.in.web;

import com.serverbe.application.port.in.art.GetRunningArtQuery;
import com.serverbe.application.port.in.art.ManageRunningArtUseCase;
import com.serverbe.application.port.in.dto.art.UpdateRunningArtCommand;
import com.serverbe.domain.model.art.RunningArt;
import com.serverbe.infrastructure.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Running Art", description = "런닝 아트 관리 API")
@RestController
@RequestMapping("/api/v1/running-arts")
@RequiredArgsConstructor
public class RunningArtController {

    private final GetRunningArtQuery getRunningArtQuery;
    private final ManageRunningArtUseCase manageRunningArtUseCase;

    @Operation(summary = "사용자별 런닝 아트 목록 조회", description = "특정 사용자가 작성한 모든 런닝 아트를 조회합니다.")
    @GetMapping("/me")
    public ApiResponse<List<RunningArt>> getByUserId(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(getRunningArtQuery.getRunningArtsByUserId(userId));
    }

    @Operation(summary = "런닝 아트 단건 조회", description = "ID를 통해 특정 런닝 아트를 상세 조회합니다.")
    @GetMapping("/{runningArtId}")
    public ApiResponse<RunningArt> getById(
            @AuthenticationPrincipal Long userId,
            @PathVariable(name = "runningArtId") Long runningArtId
    ) {
        return ApiResponse.success(getRunningArtQuery.getRunningArtById(userId, runningArtId));
    }

    @Operation(summary = "런닝 아트 수정", description = "제목과 내용을 수정합니다.")
    @PutMapping("/{runningArtId}")
    public ApiResponse<Void> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable(name = "runningArtId") Long runningArtId,
            @RequestBody @Valid UpdateRunningArtCommand request
    ) {
        manageRunningArtUseCase.updateRunningArt(userId, runningArtId, request);
        return ApiResponse.noContent();
    }

    @Operation(summary = "런닝 아트 삭제", description = "ID를 통해 특정 런닝 아트를 삭제합니다.")
    @DeleteMapping("/{runningArtId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable(name = "runningArtId") Long runningArtId
    ) {
        manageRunningArtUseCase.deleteRunningArt(userId, runningArtId);
        return ApiResponse.noContent();
    }

    @Operation(summary = "사용자의 모든 런닝 아트 삭제", description = "특정 사용자의 모든 데이터를 일괄 삭제합니다.")
    @DeleteMapping("/me")
    public ApiResponse<Void> deleteAllByUser(@AuthenticationPrincipal Long userId) {
        manageRunningArtUseCase.deleteAllRunningArtsByUserId(userId);
        return ApiResponse.noContent();
    }
}