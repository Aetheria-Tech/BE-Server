package com.serverbe.adapter.in.web;

import com.serverbe.application.port.in.me.UpdateUserUseCase;
import com.serverbe.application.port.out.dto.me.UserProfileResult;
import com.serverbe.application.port.out.dto.me.UserUpdateCommand;
import com.serverbe.application.port.in.me.GetUserUseCase;
import com.serverbe.infrastructure.common.response.RestApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User", description = "사용자 관리 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final GetUserUseCase getUserUseCase;
    private final UpdateUserUseCase updateUserUseCase;

    @Operation(summary = "내 프로필 조회")
    @GetMapping("/me")
    public RestApiResponse<UserProfileResult> getMyProfile(@AuthenticationPrincipal Long userId) {
        return RestApiResponse.success(getUserUseCase.getMyProfile(userId));
    }

    @Operation(summary = "내 프로필 수정")
    @PatchMapping("/me")
    public RestApiResponse<UserProfileResult> updateMyProfile(
            @AuthenticationPrincipal Long userId,
            @RequestBody UserUpdateCommand command
    ) {
        return RestApiResponse.success(updateUserUseCase.updateMyProfile(userId, command));
    }
}