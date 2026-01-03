package com.serverbe.adapter.in.web;

import com.serverbe.application.port.in.art.GetRunningArtUseCase;
import com.serverbe.application.port.in.art.DeleteRunningArtUseCase;
import com.serverbe.application.port.in.art.UpdateRunningArtUseCase;
import com.serverbe.adapter.in.web.dto.art.UpdateRunningArtRequest;
import com.serverbe.domain.model.art.RunningArt;
import com.serverbe.infrastructure.common.response.RestApiResponse;
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

    private final GetRunningArtUseCase getRunningArtUseCase;
    private final DeleteRunningArtUseCase deleteRunningArtUseCase;
    private final UpdateRunningArtUseCase updateRunningArtUseCase;

    @Operation(summary = "사용자별 런닝 아트 목록 조회", description = "특정 사용자가 작성한 모든 런닝 아트를 조회합니다.")
    @GetMapping("/me")
    public RestApiResponse<List<RunningArt>> getByUserId(@AuthenticationPrincipal Long userId) {
        return RestApiResponse.success(getRunningArtUseCase.getRunningArtsByUserId(userId));
    }

    @Operation(summary = "런닝 아트 단건 조회", description = "ID를 통해 특정 런닝 아트를 상세 조회합니다.")
    @GetMapping("/{runningArtId}")
    public RestApiResponse<RunningArt> getById(
            @AuthenticationPrincipal Long userId,
            @PathVariable(name = "runningArtId") Long runningArtId
    ) {
        return RestApiResponse.success(getRunningArtUseCase.getRunningArtById(userId, runningArtId));
    }

    @Operation(summary = "런닝 아트 수정", description = "제목과 내용을 수정합니다.")
    @PatchMapping("/{runningArtId}")
    public RestApiResponse<Void> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable(name = "runningArtId") Long runningArtId,
            @RequestBody @Valid UpdateRunningArtRequest request
    ) {
        updateRunningArtUseCase.updateRunningArt(userId, runningArtId, request.toCommand());
        return RestApiResponse.noContent();
    }

    @Operation(summary = "런닝 아트 삭제", description = "ID를 통해 특정 런닝 아트를 삭제합니다.")
    @DeleteMapping("/{runningArtId}")
    public RestApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable(name = "runningArtId") Long runningArtId
    ) {
        deleteRunningArtUseCase.deleteRunningArt(userId, runningArtId);
        return RestApiResponse.noContent();
    }

    @Operation(summary = "사용자의 모든 런닝 아트 삭제", description = "특정 사용자의 모든 데이터를 일괄 삭제합니다.")
    @DeleteMapping("/me")
    public RestApiResponse<Void> deleteAllByUser(@AuthenticationPrincipal Long userId) {
        deleteRunningArtUseCase.deleteAllRunningArtsByUserId(userId);
        return RestApiResponse.noContent();
    }
}