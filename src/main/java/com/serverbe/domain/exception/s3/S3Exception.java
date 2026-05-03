package com.serverbe.domain.exception.s3;

import com.serverbe.domain.exception.BusinessException;
import com.serverbe.domain.exception.ErrorCode;

public class S3Exception extends BusinessException {
    public S3Exception(ErrorCode errorCode) {
        super(errorCode);
    }

    public S3Exception(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}