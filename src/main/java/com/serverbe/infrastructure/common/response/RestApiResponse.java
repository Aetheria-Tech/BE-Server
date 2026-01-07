package com.serverbe.infrastructure.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.serverbe.infrastructure.error.ErrorMessage;
import org.springframework.http.HttpStatus;

/**
 * 전역 공통 응답 규격 (Record)
 *
 * @param success    성공 여부
 * @param httpStatus 상태 코드
 * @param data       성공 시 반환할 데이터 (성공 시에만 JSON에 포함)
 * @param error      실패 시 반환할 에러 정보 (실패 시에만 JSON에 포함)
 */
public record RestApiResponse<T>(
        boolean success,

        HttpStatus httpStatus,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        T data,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        ApiError error
) {
    /**
     * 성공 응답 - 데이터를 포함하는 경우 (200 OK)
     */
    public static <T> RestApiResponse<T> success(T data) {
        return new RestApiResponse<>(true, HttpStatus.OK, data, null);
    }


    /**
     * 실패 응답 - ErrorMessage Enum의 기본 메시지 사용
     */
    public static <T> RestApiResponse<Void> fail(ErrorMessage errorMessage) {
        return new RestApiResponse<>(
                false,
                errorMessage.getStatus(),
                null,
                new ApiError(errorMessage.getCode(), errorMessage.getMessage())
        );
    }

    /**
     * 실패 응답 - 상세 사유(Reason)를 직접 지정
     * (유효성 검사 실패 시 필드별 에러 메시지를 전달할 때 유용)
     */
    public static RestApiResponse<Void> fail(ErrorMessage errorMessage, String reason) {
        return new RestApiResponse<>(
                false,
                errorMessage.getStatus(),
                null,
                new ApiError(errorMessage.getCode(), reason)
        );
    }

    public static RestApiResponse<Void> noContent() {
        return new RestApiResponse<>(
                true,
                HttpStatus.NO_CONTENT,
                null,
                null
        );
    }

    /**
     * 에러 응답의 상세 구조를 담는 내부 Record
     */
    public record ApiError(
            String code,
            String message
    ) {
    }
}