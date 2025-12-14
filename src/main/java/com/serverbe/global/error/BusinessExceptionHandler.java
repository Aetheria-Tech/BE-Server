package com.serverbe.global.error;

import com.serverbe.global.common.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 애플리케이션 전역에서 발생하는 예외를 처리하는 RestControllerAdvice 입니다.
 */
@Slf4j
@RestControllerAdvice
public class BusinessExceptionHandler {

    /**
     * 커스텀 비즈니스 예외 (BusinessException)
     * - `ErrorMessage`를 포함하여, 예측 가능한 예외 상황을 처리합니다.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        var errorMessage = e.getErrorMessage();

        // 4xx, 5xx 등 예측된 비즈니스 예외는 WARN 레벨로 기록합니다.
        log.warn("[WARN] BusinessException -> {}", errorMessage.getMessage());

        return ResponseEntity.status(errorMessage.getStatus())
                .body(ApiResponse.fail(errorMessage, errorMessage.getMessage()));
    }

    /**
     * {@code @Valid} 로 유효성 검사에 실패한 경우 (RequestBody DTO)
     * - FieldErrors와 GlobalErrors를 모두 처리하여 상세한 메시지를 반환합니다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        // FieldErrors: "email: 이메일 형식이 아닙니다."
        String detailedErrorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(error -> String.format("[%s]: %s", error.getField(), error.getDefaultMessage()))
                .collect(Collectors.joining(", "));

        // GlobalErrors: "passwordConfirm: 비밀번호가 일치하지 않습니다."
        String globalErrorMessage = e.getBindingResult().getGlobalErrors().stream()
                .map(error -> String.format("[%s]: %s", error.getObjectName(), error.getDefaultMessage()))
                .collect(Collectors.joining(", "));

        // 두 에러 메시지를 결합
        String combinedMessage = Stream.of(detailedErrorMessage, globalErrorMessage)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("; "));

        String finalMessage = combinedMessage.isEmpty()
                ? ErrorMessage.INVALID_REQUEST_PARAMETER.getMessage()
                : combinedMessage;

        // 400번대 클라이언트 오류는 WARN 레벨로 기록합니다.
        log.warn("[WARN] MethodArgumentNotValidException -> {}", finalMessage);

        return ResponseEntity.status(ErrorMessage.INVALID_REQUEST_PARAMETER.getStatus())
                .body(ApiResponse.fail(ErrorMessage.INVALID_REQUEST_PARAMETER, finalMessage));
    }

    /**
     * {@code @Validated} 로 유효성 검사에 실패한 경우 (메서드 파라미터)
     * (e.g., @RequestParam, @PathVariable)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException e) {

        String detailedErrorMessage = e.getConstraintViolations().stream()
                .map(violation -> {
                    // Path API를 사용하여 마지막 노드 추출
                    var pathIterator = violation.getPropertyPath().iterator();
                    String parameterName = "";
                    while (pathIterator.hasNext()) {
                        parameterName = pathIterator.next().getName();
                    }
                    return String.format("[%s]: %s", parameterName, violation.getMessage());
                })
                .collect(Collectors.joining(", "));

        log.warn("[WARN] ConstraintViolationException -> {}", detailedErrorMessage);

        return ResponseEntity.status(ErrorMessage.INVALID_REQUEST_PARAMETER.getStatus())
                .body(ApiResponse.fail(ErrorMessage.INVALID_REQUEST_PARAMETER, detailedErrorMessage));
    }

    /**
     * 필수 요청 파라미터(@RequestParam)가 누락된 경우
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {

        String detailedErrorMessage = String.format("필수 파라미터 [%s](이)가 누락되었습니다.", e.getParameterName());

        log.warn("[WARN] MissingServletRequestParameterException -> {}", detailedErrorMessage);

        return ResponseEntity.status(ErrorMessage.INVALID_REQUEST_PARAMETER.getStatus())
                .body(ApiResponse.fail(ErrorMessage.INVALID_REQUEST_PARAMETER, detailedErrorMessage));
    }

    /**
     * 잘못된 JSON 형식을 요청한 경우
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {

        // e.getMessage()는 너무 길고 복잡하며 내부 구현을 노출할 수 있으므로, ErrorMessage의 기본 메시지를 사용합니다.
        log.warn("[WARN] HttpMessageNotReadableException -> {}", e.getMostSpecificCause().getMessage());

        return ResponseEntity.status(ErrorMessage.MALFORMED_JSON_REQUEST.getStatus())
                .body(ApiResponse.fail(ErrorMessage.MALFORMED_JSON_REQUEST));
    }

    /**
     * 그 외 모든 예외 (Catch-all 500)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        // [중요] 처리되지 않은 500번대 서버 오류는 ERROR 레벨로 기록하고,
        // 클라이언트에게는 상세한 예외 내용을 노출하지 않습니다.
        log.error("[ERROR] Unhandled Exception", e); // 스택 트레이스 전체를 기록

        return ResponseEntity.status(ErrorMessage.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.fail(ErrorMessage.INTERNAL_SERVER_ERROR));
    }
}