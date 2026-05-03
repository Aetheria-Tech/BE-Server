package com.serverbe.domain.exception.crypto;

import com.serverbe.domain.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CryptoErrorCode implements ErrorCode {
    INCORRECT_CIPHERTEXT_FORMAT(HttpStatus.INTERNAL_SERVER_ERROR, "CRYPTO_001", "잘못된 암호화 포멧입니다."),
    DECRYPTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "CRYPTO_002", "복호화 실패"),
    ENCRYPTION_FAILURE(HttpStatus.INTERNAL_SERVER_ERROR, "CRYPTO_003", "암호화 실패");

    private final HttpStatus status;
    private final String code;
    private final String message;
}