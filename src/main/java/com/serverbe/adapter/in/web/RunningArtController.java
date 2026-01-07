package com.serverbe.adapter.in.web;

import com.serverbe.adapter.in.web.dto.art.RunningArtResponse;
import com.serverbe.application.port.in.art.GetRunningArtUseCase;
import com.serverbe.application.port.in.art.DeleteRunningArtUseCase;
import com.serverbe.application.port.in.art.UpdateRunningArtUseCase;
import com.serverbe.adapter.in.web.dto.art.UpdateRunningArtRequest;
import com.serverbe.infrastructure.common.response.RestApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

    @Operation(
            summary = "내 런닝 아트 목록 조회",
            description = "현재 로그인한 사용자가 생성한 모든 런닝 아트 목록을 최신순으로 조회합니다.",
            security = @SecurityRequirement(name = "jwtAuth"), // JWT 인증이 필요함을 명시
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "조회 성공",
                            useReturnTypeSchema = true
                    ),
                    @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
                    @ApiResponse(responseCode = "500", description = "서버 내부 오류")
            }
    )
    @GetMapping("/me")
    public RestApiResponse<List<RunningArtResponse>> getByUserId(@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        return RestApiResponse.success(getRunningArtUseCase.getRunningArtsByUserId(userId).stream().map(RunningArtResponse::toResponse).toList());
    }

    @Operation(
            summary = "런닝 아트 단건 상세 조회",
            description = "런닝 아트의 고유 ID를 사용하여 상세 정보(제목, 내용, Polyline 경로 등)를 조회합니다.",
            security = @SecurityRequirement(name = "jwtAuth"),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "조회 성공",
                            useReturnTypeSchema = true
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "해당 ID의 런닝 아트를 찾을 수 없음",
                            content = @Content(schema = @Schema(implementation = RestApiResponse.class))
                    ),
                    @ApiResponse(responseCode = "401", description = "인증 실패")
            }
    )
    @GetMapping("/{runningArtId}")
    public RestApiResponse<RunningArtResponse> getById(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "조회할 런닝 아트 ID", example = "1", required = true)
            @PathVariable(name = "runningArtId") Long runningArtId
    ) {
        return RestApiResponse.success(RunningArtResponse.toResponse(getRunningArtUseCase.getRunningArtById(userId, runningArtId)));
    }

    @Operation(
            summary = "런닝 아트 수정",
            description = "런닝 아트의 제목과 상세 내용을 수정합니다. 작성자 본인만 수정 가능합니다.",
            security = @SecurityRequirement(name = "jwtAuth"),
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "수정 성공 (데이터 반환 없음)",
                            useReturnTypeSchema = true
                    ),
                    @ApiResponse(responseCode = "400", description = "입력 데이터 유효성 검증 실패"),
                    @ApiResponse(responseCode = "401", description = "인증 실패"),
                    @ApiResponse(responseCode = "403", description = "수정 권한 없음 (작성자 아님)"),
                    @ApiResponse(responseCode = "404", description = "수정할 런닝 아트를 찾을 수 없음")
            }
    )
    @PatchMapping("/{runningArtId}")
    public RestApiResponse<Void> update(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "수정할 런닝 아트 ID", example = "1", required = true)
            @PathVariable(name = "runningArtId") Long runningArtId,
            @RequestBody @Valid UpdateRunningArtRequest request
    ) {
        updateRunningArtUseCase.updateRunningArt(userId, runningArtId, request.toCommand());
        return RestApiResponse.noContent();
    }

    @Operation(
            summary = "런닝 아트 삭제",
            description = "런닝 아트의 고유 ID를 사용하여 리소스를 삭제합니다. 작성자 본인만 삭제할 수 있습니다.",
            security = @SecurityRequirement(name = "jwtAuth"),
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "삭제 성공 (데이터 반환 없음)"
                    ),
                    @ApiResponse(responseCode = "401", description = "인증 실패"),
                    @ApiResponse(responseCode = "403", description = "삭제 권한 없음 (작성자 아님)"),
                    @ApiResponse(responseCode = "404", description = "삭제할 런닝 아트를 찾을 수 없음"),
                    @ApiResponse(responseCode = "500", description = "서버 오류")
            }
    )
    @DeleteMapping("/{runningArtId}")
    public RestApiResponse<Void> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "삭제할 런닝 아트 ID", example = "1", required = true)
            @PathVariable(name = "runningArtId") Long runningArtId
    ) {
        deleteRunningArtUseCase.deleteRunningArt(userId, runningArtId);
        return RestApiResponse.noContent();
    }

    @Operation(
            summary = "내 모든 런닝 아트 삭제",
            description = "현재 로그인한 사용자가 작성한 모든 런닝 아트 데이터를 일괄 삭제합니다. 이 작업은 되돌릴 수 없으니 주의하십시오.",
            security = @SecurityRequirement(name = "jwtAuth"),
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "일괄 삭제 성공 (데이터 반환 없음)"
                    ),
                    @ApiResponse(responseCode = "401", description = "인증 실패"),
                    @ApiResponse(responseCode = "500", description = "서버 내부 오류 (삭제 작업 실패)")
            }
    )
    @DeleteMapping("/me")
    public RestApiResponse<Void> deleteAllByUser(@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        deleteRunningArtUseCase.deleteAllRunningArtsByUserId(userId);
        return RestApiResponse.noContent();
    }
}