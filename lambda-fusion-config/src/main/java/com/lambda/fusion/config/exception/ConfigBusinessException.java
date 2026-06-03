package com.lambda.fusion.config.exception;

import com.lambda.cloud.core.exception.model.ErrorCode;
import com.lambda.cloud.mvc.execption.BusinessException;

public class ConfigBusinessException extends BusinessException {
    public ConfigBusinessException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ConfigBusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public ConfigBusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getCode(), errorCode.getMessage(), null, cause);
    }

    public ConfigBusinessException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode.getCode(), errorCode.getMessage(), args, cause);
    }
}
