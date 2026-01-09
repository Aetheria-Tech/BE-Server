package com.serverbe.infrastructure.error;

import com.serverbe.infrastructure.common.response.RestApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @responsibility 애플리케이션 전역에서 발생하는 예외를 가로채어 공통 응답 규격인 {@link RestApiResponse} 형태로 변환하여 반환합니다.
 * @implSpec 1. <b>비즈니스 예외</b>: {@link BusinessException}을 통해 의도된 에러 상황을 처리합니다.<br>
 * 2. <b>유효성 검증</b>: Bean Validation 실패 시 필드별 상세 에러 메시지를 생성합니다.<br>
 * 3. <b>보안 예외</b>: 인증/인가 실패에 대한 적절한 HTTP 상태 코드를 매핑합니다.<br>
 * 4. <b>로깅 전략</b>: 4xx 계열은 WARN 레벨로, 5xx 계열 및 미처리 예외는 ERROR 레벨로 스택 트레이스와 함께 기록합니다.
 */
@Slf4j
@RestControllerAdvice
public class BusinessExceptionHandler {

    /**
     * @param e {@link BusinessException} 인스턴스
     * @return {@link ErrorMessage}에 정의된 상태 코드와 메시지를 포함한 응답
     * @responsibility 커스텀 비즈니스 예외를 처리합니다.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<RestApiResponse<Void>> handleBusinessException(BusinessException e) {
        var errorMessage = e.getErrorMessage();
        log.warn("[WARN] BusinessException -> {}", errorMessage.getMessage());

        return ResponseEntity.status(errorMessage.getStatus())
                .body(RestApiResponse.fail(errorMessage, errorMessage.getMessage()));
    }

    /**
     * @param e {@link MethodArgumentNotValidException}
     * @return 400 Bad Request와 상세 필드 에러 메시지
     * @responsibility <b>@Valid</b> 어노테이션을 통한 DTO 유효성 검사 실패 시 호출됩니다.
     * @implNote 여러 필드에서 발생한 에러를 스트림으로 수집하여 <b>"[필드명]: 메시지"</b> 형태의 상세 정보를 제공합니다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RestApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String detailedErrorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(error -> String.format("[%s]: %s", error.getField(), error.getDefaultMessage()))
                .collect(Collectors.joining(", "));

        String globalErrorMessage = e.getBindingResult().getGlobalErrors().stream()
                .map(error -> String.format("[%s]: %s", error.getObjectName(), error.getDefaultMessage()))
                .collect(Collectors.joining(", "));

        String combinedMessage = Stream.of(detailedErrorMessage, globalErrorMessage)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("; "));

        String finalMessage = combinedMessage.isEmpty()
                ? ErrorMessage.INVALID_REQUEST_PARAMETER.getMessage()
                : combinedMessage;

        log.warn("[WARN] MethodArgumentNotValidException -> {}", finalMessage);

        return ResponseEntity.status(ErrorMessage.INVALID_REQUEST_PARAMETER.getStatus())
                .body(RestApiResponse.fail(ErrorMessage.INVALID_REQUEST_PARAMETER, finalMessage));
    }

    /**
     * @param e {@link ConstraintViolationException}
     * @responsibility <b>@Validated</b>를 통한 메서드 파라미터 유효성 검사 실패 시 호출됩니다.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<RestApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException e) {
        String detailedErrorMessage = e.getConstraintViolations().stream()
                .map(violation -> {
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
                .body(RestApiResponse.fail(ErrorMessage.INVALID_REQUEST_PARAMETER, detailedErrorMessage));
    }

    /**
     * @param e {@link MissingServletRequestParameterException}
     * @responsibility 필수 요청 파라미터가 누락된 경우를 처리합니다.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<RestApiResponse<Void>> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        String detailedErrorMessage = String.format("필수 파라미터 [%s](이)가 누락되었습니다.", e.getParameterName());
        log.warn("[WARN] MissingServletRequestParameterException -> {}", detailedErrorMessage);

        return ResponseEntity.status(ErrorMessage.INVALID_REQUEST_PARAMETER.getStatus())
                .body(RestApiResponse.fail(ErrorMessage.INVALID_REQUEST_PARAMETER, detailedErrorMessage));
    }

    /**
     * @param e {@link HttpMessageNotReadableException}
     * @responsibility JSON 형식이 잘못되어 역직렬화에 실패한 경우를 처리합니다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RestApiResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("[WARN] HttpMessageNotReadableException -> {}", e.getMostSpecificCause().getMessage());

        return ResponseEntity.status(ErrorMessage.MALFORMED_JSON_REQUEST.getStatus())
                .body(RestApiResponse.fail(ErrorMessage.MALFORMED_JSON_REQUEST));
    }

    /**
     * @param e {@link Exception}
     * @responsibility 처리되지 않은 모든 런타임 예외를 위한 최후의 방어선입니다.
     * @implNote 보안상의 이유로 클라이언트에게는 상세 스택 트레이스를 숨기고 <b>500 Internal Server Error</b> 규격만 반환합니다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RestApiResponse<Void>> handleException(Exception e) {
        log.error("[ERROR] Unhandled Exception", e);

        return ResponseEntity.status(ErrorMessage.INTERNAL_SERVER_ERROR.getStatus())
                .body(RestApiResponse.fail(ErrorMessage.INTERNAL_SERVER_ERROR));
    }

    /**
     * @responsibility <b>401 Unauthorized</b> 관련 보안 예외를 처리합니다.
     */
    @ExceptionHandler({AuthenticationException.class, InsufficientAuthenticationException.class})
    public ResponseEntity<RestApiResponse<Void>> handleAuthenticationException(Exception e) {
        log.warn("[WARN] Unauthorized -> {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(RestApiResponse.fail(ErrorMessage.UNAUTHORIZED));
    }

    /**
     * @responsibility <b>403 Forbidden</b> 관련 보안 예외를 처리합니다.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<RestApiResponse<Void>> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("[WARN] Access Denied -> {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(RestApiResponse.fail(ErrorMessage.ACCESS_DENIED));
    }
}