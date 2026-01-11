package com.serverbe.domain.exception.art;

import com.serverbe.domain.exception.BusinessException;
import com.serverbe.domain.exception.ErrorCode;

public class ArtException extends BusinessException {
    public ArtException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ArtException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}