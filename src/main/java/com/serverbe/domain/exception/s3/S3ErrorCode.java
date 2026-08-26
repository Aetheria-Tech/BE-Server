package com.serverbe.domain.exception.s3;

import com.serverbe.domain.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum S3ErrorCode implements ErrorCode {
    S3_UPLOAD_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "S3_001", "S3 입력 버킷 업로드 중 오류가 발생했습니다."),
    S3_DOWNLOAD_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "S3_002", "S3 결과 다운로드 및 파싱 중 오류 발생."),
    S3_DELETE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "S3_003", "S3 파일 삭제 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}