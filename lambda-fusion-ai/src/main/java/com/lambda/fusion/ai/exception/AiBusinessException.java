package com.lambda.fusion.ai.exception;

import com.lambda.cloud.core.exception.model.ErrorCode;
import com.lambda.cloud.mvc.execption.BusinessException;

/**
 * AI模块业务异常
 * <p>
 * 继承自统一的BusinessException，用于AI模块的业务异常处理
 * 提供便捷的构造方法，支持错误码和参数化消息
 *
 * @author Jin
 */
public class AiBusinessException extends BusinessException {

    /**
     * 使用错误码构造异常
     *
     * @param errorCode 错误码枚举
     */
    public AiBusinessException(ErrorCode errorCode) {
        super(errorCode);
    }

    /**
     * 使用错误码和参数构造异常
     * <p>
     * 支持消息模板参数化，例如：
     * <pre>
     * throw new AiBusinessException(AiErrorCode.DOCUMENT_NOT_FOUND, docId);
     * </pre>
     *
     * @param errorCode 错误码枚举
     * @param args      消息参数
     */
    public AiBusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    /**
     * 使用错误码和原始异常构造异常
     * <p>
     * 用于包装底层异常，保留异常堆栈信息
     *
     * @param errorCode 错误码枚举
     * @param cause     原始异常
     */
    public AiBusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getCode(), errorCode.getMessage(), null, cause);
    }

    /**
     * 使用错误码、参数和原始异常构造异常
     *
     * @param errorCode 错误码枚举
     * @param cause     原始异常
     * @param args      消息参数
     */
    public AiBusinessException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode.getCode(), errorCode.getMessage(), args, cause);
    }

    /**
     * 便捷方法：知识库不存在异常
     *
     * @param kbId 知识库ID
     * @return AiBusinessException
     */
    public static AiBusinessException knowledgeBaseNotFound(String kbId) {
        return new AiBusinessException(AiErrorCode.KNOWLEDGE_BASE_NOT_FOUND, kbId);
    }

    /**
     * 便捷方法：文档不存在异常
     *
     * @param docId 文档ID
     * @return AiBusinessException
     */
    public static AiBusinessException documentNotFound(String docId) {
        return new AiBusinessException(AiErrorCode.DOCUMENT_NOT_FOUND, docId);
    }

    /**
     * 便捷方法：会话不存在异常
     *
     * @param sessionId 会话ID
     * @return AiBusinessException
     */
    public static AiBusinessException sessionNotFound(String sessionId) {
        return new AiBusinessException(AiErrorCode.SESSION_NOT_FOUND, sessionId);
    }

    /**
     * 便捷方法：消息不存在异常
     *
     * @param messageId 消息ID
     * @return AiBusinessException
     */
    public static AiBusinessException messageNotFound(Long messageId) {
        return new AiBusinessException(AiErrorCode.MESSAGE_NOT_FOUND, messageId);
    }

    /**
     * 便捷方法：LLM模型不存在异常
     *
     * @param modelId 模型ID
     * @return AiBusinessException
     */
    public static AiBusinessException llmModelNotFound(String modelId) {
        return new AiBusinessException(AiErrorCode.LLM_MODEL_NOT_FOUND, modelId);
    }

    /**
     * 便捷方法：AI机器人不存在异常
     *
     * @param robotId 机器人ID
     * @return AiBusinessException
     */
    public static AiBusinessException robotNotFound(String robotId) {
        return new AiBusinessException(AiErrorCode.ROBOT_NOT_FOUND, robotId);
    }

}
