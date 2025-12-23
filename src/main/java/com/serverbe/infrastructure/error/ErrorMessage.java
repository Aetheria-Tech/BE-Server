package com.serverbe.infrastructure.error;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 에러가 발생했을 때 HTTP 상태 코드와 메시지를 반환하는 열거형
 *
 * <li>열거형의 접미어로 Exception을 붙이지 않는다.</li>
 * <li>네이밍 일관성을 가지게 한다. NOT_FOUND_XXX, INVALID_XXX, FAILED_XXX, DUPLICATE_XXX 등으로 통일</li>
 *
 * @author duskafka
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public enum ErrorMessage {
    // SERVER
    INVALID_REQUEST_PARAMETER(HttpStatus.BAD_REQUEST, "COMMON_001", "잘못된 요청 파라미터입니다."),
    MALFORMED_JSON_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_002", "잘못된 형식의 JSON 요청입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_003", "서버 내부 오류가 발생했습니다."),

    // IMAGE_METADATA
    NOT_FOUND_IMAGE_METADATA(HttpStatus.NOT_FOUND, "IMAGE_METADATA_001", "이미지 메타데이터를 찾을 수 없습니다."),
    FORBIDDEN_IMAGE_METADATA(HttpStatus.FORBIDDEN, "IMAGE_METADATA_002", "이미지 메타데이터에 대한 권한이 없습니다."),

    // EXTERNAL API
    WITHDRAWAL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "EXTERNAL_001","회원 탈퇴 서버에서 회원 탈퇴를 실패했습니다."),
    EXTERNAL_API_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "EXTERNAL_002","외부 API 응답이 잘못되었습니다."),

    // KAKAO / API
    NOT_FOUND_KAKAO_TOKEN(HttpStatus.NOT_FOUND, "KAKAO_001", "카카오 토큰을 찾을 수 없습니다."),
    FAILED_KAKAO_API(HttpStatus.BAD_GATEWAY, "KAKAO_002", "카카오 API 호출에 실패했습니다."),

    // GOOGLE /API
    FAILED_GOOGLE_API(HttpStatus.BAD_GATEWAY, "GOOGLE_001", "구글 API 호출에 실패했습니다."),

    // GEOCODING
    FAILED_GEOCODING_API(HttpStatus.BAD_GATEWAY, "GEOCODE_001","지오코딩 API 호출에 실패했습니다."),
    INVALID_ADDRESS(HttpStatus.BAD_REQUEST, "GEOCODE_002","주소가 올바르지 않습니다"),


    // USER
    NOT_FOUND_USER(HttpStatus.NOT_FOUND, "RUNNER_002", "사용자를 찾을 수 없습니다."),
    FORBIDDEN_USER(HttpStatus.FORBIDDEN, "RUNNER_003", "사용자에 대한 권한이 없습니다."),

    // ART
    NOT_FOUND_RUNNING_ART(HttpStatus.NOT_FOUND, "ART_001", "런닝아트를 찾을 수 없습니다"),
    USER_IS_NOT_OWNER_OF_RUNNING_ART(HttpStatus.FORBIDDEN, "ART_002", "사용자는 런닝아트의 소유자가 아닙니다."),

    // AUTH (SecurityConfig 및 Handler에서 사용)
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_001", "로그인이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_002", "해당 요청에 대한 권한이 없습니다."),

    // JWT
    JWT_SUBJECT_IS_NOT_NUMBER(HttpStatus.BAD_REQUEST, "JWT_001", "JWT 토큰 값이 유효하지 않습니다."),
    JWT_TOKEN_IS_EMPTY(HttpStatus.BAD_REQUEST, "JWT_002", "JWT 토큰 값이 비어있습니다."),
    JWT_TOKEN_IS_INVALID(HttpStatus.BAD_REQUEST, "JWT_003", "JWT 토큰이 유효하지 않습니다."),
    JWT_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "JWT_004", "만료된 JWT 토큰입니다."),
    JWT_TOKEN_UNSUPPORTED(HttpStatus.BAD_REQUEST, "JWT_005", "지원되지 않는 JWT 토큰 형식입니다."),

    // TOKEN (Refresh Token / Redis 관련)
    NOT_FOUND_REFRESH_TOKEN(HttpStatus.NOT_FOUND, "TOKEN_001", "리프레시 토큰을 찾을 수 없습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.BAD_REQUEST, "TOKEN_002", "유효하지 않은 리프레시 토큰입니다."),
    ACCESS_TOKEN_NOT_EXIST(HttpStatus.BAD_REQUEST, "TOKEN_003", "액세스 토큰이 존재하지 않습니다."),
    REFRESH_TOKEN_NOT_EXIST(HttpStatus.BAD_REQUEST, "TOKEN_004", "리프레시 토큰이 존재하지 않습니다.")
    ;


    private final HttpStatus status;
    private final String code;
    private final String message;
}