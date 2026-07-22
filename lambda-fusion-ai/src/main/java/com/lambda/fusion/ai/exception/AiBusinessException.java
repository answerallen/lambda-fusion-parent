package com.lambda.fusion.ai.exception;

import com.lambda.cloud.core.exception.model.ErrorCode;
import com.lambda.cloud.mvc.execption.BusinessException;

/**
 * AI 模块业务异常，继承 BusinessException，基于错误码与参数化消息构造
 *
 * @author Jin
 */
public class AiBusinessException extends BusinessException {

    public AiBusinessException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AiBusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public AiBusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getCode(), errorCode.getMessage(), null, cause);
    }

    public AiBusinessException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode.getCode(), errorCode.getMessage(), args, cause);
    }

    public static AiBusinessException tooManyRequests() {
        return new AiBusinessException(AiErrorCode.RATE_LIMITED);
    }
}
