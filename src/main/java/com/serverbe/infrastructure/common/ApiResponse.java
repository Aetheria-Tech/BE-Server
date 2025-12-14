package com.serverbe.infrastructure.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.serverbe.infrastructure.error.ErrorMessage;

/**
 * 전역 공통 응답 규격 (Record)
 * @param success 성공 여부
 * @param data    성공 시 반환할 데이터 (성공 시에만 JSON에 포함)
 * @param error   실패 시 반환할 에러 정보 (실패 시에만 JSON에 포함)
 */
public record ApiResponse<T>(
        boolean success,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        T data,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        ApiError error
) {
    /**
     * 성공 응답 - 데이터를 포함하는 경우 (200 OK)
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * 실패 응답 - ErrorMessage Enum의 기본 메시지 사용
     */
    public static ApiResponse<Void> fail(ErrorMessage errorMessage) {
        return new ApiResponse<>(
                false,
                null,
                new ApiError(errorMessage.getCode(), errorMessage.getMessage())
        );
    }

    /**
     * 실패 응답 - 상세 사유(Reason)를 직접 지정
     * (유효성 검사 실패 시 필드별 에러 메시지를 전달할 때 유용)
     */
    public static ApiResponse<Void> fail(ErrorMessage errorMessage, String reason) {
        return new ApiResponse<>(
                false,
                null,
                new ApiError(errorMessage.getCode(), reason)
        );
    }

    /**
     * 에러 응답의 상세 구조를 담는 내부 Record
     */
    public record ApiError(
            String code,
            String message
    ) { }
}