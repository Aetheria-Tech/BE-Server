package com.serverbe.adapter.in.web;

import com.serverbe.application.port.in.dto.UserProfileResponse;
import com.serverbe.application.port.in.dto.UserUpdateCommand;
import com.serverbe.application.port.in.me.UserUseCase;
import com.serverbe.infrastructure.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Tag(name = "User", description = "사용자 관리 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserUseCase userUseCase;

    @Operation(summary = "내 프로필 조회")
    @GetMapping("/me")
    public Mono<ApiResponse<UserProfileResponse>> getMyProfile(@AuthenticationPrincipal Long userId) {
        return Mono.fromCallable(() -> userUseCase.getMyProfile(userId)) // 데이터 가져오기 (Blocking)
                .subscribeOn(Schedulers.boundedElastic())
                .map(ApiResponse::success); // 결과 포맷팅
    }

    @Operation(summary = "내 프로필 수정")
    @PatchMapping("/me")
    public Mono<ApiResponse<UserProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal Long userId,
            @RequestBody UserUpdateCommand command
    ) {
        return Mono.fromCallable(() -> userUseCase.updateMyProfile(userId, command))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ApiResponse::success);
    }
}