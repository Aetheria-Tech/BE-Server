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
    @GetMapping("/user/{userId}")
    public ApiResponse<List<RunningArt>> getByUserId(@PathVariable Long userId) {
        return ApiResponse.success(getRunningArtQuery.getRunningArtsByUserId(userId));
    }

    @Operation(summary = "런닝 아트 단건 조회", description = "ID를 통해 특정 런닝 아트를 상세 조회합니다.")
    @GetMapping("/{id}")
    public ApiResponse<RunningArt> getById(@PathVariable Long id) {
        return ApiResponse.success(getRunningArtQuery.getRunningArtById(id));
    }

    @Operation(summary = "런닝 아트 수정", description = "제목과 내용을 수정합니다.")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable Long id,
            @RequestBody @Valid UpdateRunningArtCommand request
    ) {
        manageRunningArtUseCase.updateRunningArt(id, request);
        return ApiResponse.noContent();
    }

    @Operation(summary = "런닝 아트 삭제", description = "ID를 통해 특정 런닝 아트를 삭제합니다.")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        manageRunningArtUseCase.deleteRunningArt(id);
        return ApiResponse.noContent();
    }

    @Operation(summary = "사용자의 모든 런닝 아트 삭제", description = "특정 사용자의 모든 데이터를 일괄 삭제합니다.")
    @DeleteMapping("/user/{userId}")
    public ApiResponse<Void> deleteAllByUser(@PathVariable Long userId) {
        manageRunningArtUseCase.deleteAllRunningArtsByUserId(userId);
        return ApiResponse.noContent();
    }
}