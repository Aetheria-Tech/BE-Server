package com.serverbe.domain.exception.auth;

import com.serverbe.domain.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {
    // Refresh Token (RTR 관련)
    REISSUE_FAILED(HttpStatus.FORBIDDEN, "AUTH_101", "이미 사용된 리프레시 토큰이거나 보안 위협이 감지되었습니다."),

    // AUTH (SecurityConfig 및 Handler에서 사용)
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_201", "로그인이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_202", "해당 요청에 대한 권한이 없습니다."),

    // OAUTH
    UNSUPPORTED_SOCIAL_LOGIN(HttpStatus.BAD_REQUEST, "OAUTH_001", "지원하지 않는 소셜 로그인입니다."),

    // JWT
    JWT_SUBJECT_IS_NOT_NUMBER(HttpStatus.BAD_REQUEST, "JWT_001", "JWT 토큰 값이 유효하지 않습니다."),
    JWT_TOKEN_IS_EMPTY(HttpStatus.BAD_REQUEST, "JWT_002", "JWT 토큰 값이 비어있습니다."),
    JWT_TOKEN_IS_INVALID(HttpStatus.BAD_REQUEST, "JWT_003", "JWT 토큰이 유효하지 않습니다."),
    JWT_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "JWT_004", "만료된 JWT 토큰입니다."),
    JWT_TOKEN_UNSUPPORTED(HttpStatus.BAD_REQUEST, "JWT_005", "지원되지 않는 JWT 토큰 형식입니다."),
    JWT_TOKEN_IS_LOGOUT(HttpStatus.UNAUTHORIZED, "JWT_006", "로그아웃된 JWT 토큰입니다."),
    JWT_KEY_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "JWT_101", "JWT 키가 올바르지 않습니다."),
    JWT_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "JWT_102", "JWT 토큰 생성 중 오류가 발생했습니다."),

    // TOKEN (Refresh Token / Redis 관련)
    NOT_FOUND_REFRESH_TOKEN(HttpStatus.NOT_FOUND, "TOKEN_001", "리프레시 토큰을 찾을 수 없습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.BAD_REQUEST, "TOKEN_002", "유효하지 않은 리프레시 토큰입니다."),
    ACCESS_TOKEN_NOT_EXIST(HttpStatus.BAD_REQUEST, "TOKEN_003", "액세스 토큰이 존재하지 않습니다."),
    REFRESH_TOKEN_NOT_EXIST(HttpStatus.BAD_REQUEST, "TOKEN_004", "리프레시 토큰이 존재하지 않습니다."),


    // HASH
    FAILED_HASH_REFRESH_TOKEN(HttpStatus.INTERNAL_SERVER_ERROR, "HASH_001", "리프레시 토큰 해싱에 실패했습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}