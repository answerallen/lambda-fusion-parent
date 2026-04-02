package com.lambda.fusion.ai.commons.exception;

import com.lambda.cloud.core.exception.model.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI模块错误码枚举
 * <p>
 * 定义AI模块所有业务错误码，统一管理错误信息
 * 错误码规则：AI模块使用 30000-39999 范围
 *
 * @author Jin
 */
@Getter
@AllArgsConstructor
public enum AiErrorCode implements ErrorCode {

    // ========== 知识库相关错误 (30000-30099) ==========
    /**
     * 知识库不存在
     */
    KNOWLEDGE_BASE_NOT_FOUND(30001, "知识库不存在"),

    /**
     * 知识库已被删除
     */
    KNOWLEDGE_BASE_DELETED(30002, "知识库已被删除"),

    /**
     * 知识库名称已存在
     */
    KNOWLEDGE_BASE_NAME_EXISTS(30003, "知识库名称已存在"),

    /**
     * 知识库状态异常
     */
    KNOWLEDGE_BASE_STATUS_INVALID(30004, "知识库状态异常，无法执行此操作"),

    // ========== 文档相关错误 (30100-30199) ==========
    /**
     * 文档不存在
     */
    DOCUMENT_NOT_FOUND(30101, "文档不存在"),

    /**
     * 文件大小超过限制
     */
    FILE_SIZE_EXCEEDED(30102, "文件大小超过限制"),

    /**
     * 不支持的文件类型
     */
    FILE_TYPE_NOT_SUPPORTED(30103, "不支持的文件类型"),

    /**
     * 文件哈希计算失败
     */
    FILE_HASH_CALCULATION_FAILED(30104, "文件哈希计算失败"),

    /**
     * 文件保存失败
     */
    FILE_SAVE_FAILED(30105, "文件保存失败"),

    /**
     * 文件不存在
     */
    FILE_NOT_FOUND(30106, "文件不存在"),

    /**
     * 文档内容为空
     */
    DOCUMENT_CONTENT_EMPTY(30107, "文档内容为空"),

    /**
     * 文档已存在
     */
    DOCUMENT_ALREADY_EXISTS(30108, "文档已存在"),

    /**
     * 文档处理中
     */
    DOCUMENT_PROCESSING(30109, "文档正在处理中，请稍后"),

    /**
     * 文档处理失败
     */
    DOCUMENT_PROCESS_FAILED(30110, "文档处理失败"),

    // ========== 会话相关错误 (30200-30299) ==========
    /**
     * 会话不存在
     */
    SESSION_NOT_FOUND(30201, "会话不存在"),

    /**
     * 会话已归档
     */
    SESSION_ARCHIVED(30202, "会话已归档，无法继续对话"),

    /**
     * 会话状态异常
     */
    SESSION_STATUS_INVALID(30203, "会话状态异常"),

    // ========== 消息相关错误 (30300-30399) ==========
    /**
     * 消息不存在
     */
    MESSAGE_NOT_FOUND(30301, "消息不存在"),

    /**
     * 消息内容为空
     */
    MESSAGE_CONTENT_EMPTY(30302, "消息内容不能为空"),

    /**
     * 消息发送失败
     */
    MESSAGE_SEND_FAILED(30303, "消息发送失败"),

    // ========== LLM模型相关错误 (30400-30499) ==========
    /**
     * LLM模型不存在
     */
    LLM_MODEL_NOT_FOUND(30401, "LLM模型配置不存在"),

    /**
     * 默认LLM模型未配置
     */
    DEFAULT_LLM_MODEL_NOT_CONFIGURED(30402, "未配置默认LLM模型"),

    /**
     * LLM模型未启用
     */
    LLM_MODEL_DISABLED(30403, "LLM模型未启用"),

    /**
     * LLM API调用失败
     */
    LLM_API_CALL_FAILED(30404, "LLM API调用失败"),

    /**
     * LLM模型提供商不支持
     */
    LLM_PROVIDER_NOT_SUPPORTED(30405, "不支持的LLM模型提供商"),

    // ========== RAG相关错误 (30500-30599) ==========
    /**
     * 向量检索失败
     */
    VECTOR_SEARCH_FAILED(30501, "向量检索失败"),

    /**
     * 向量化失败
     */
    EMBEDDING_FAILED(30502, "文本向量化失败"),

    /**
     * 提示词模板不存在
     */
    PROMPT_TEMPLATE_NOT_FOUND(30503, "提示词模板不存在"),

    /**
     * 系统模板不允许修改
     */
    SYSTEM_TEMPLATE_NOT_EDITABLE(30504, "系统模板不允许修改或删除"),

    /**
     * RAG增强失败
     */
    RAG_ENHANCEMENT_FAILED(30504, "RAG增强失败"),

    // ========== 向量存储相关错误 (30600-30699) ==========
    /**
     * 向量表不存在
     */
    VECTOR_TABLE_NOT_FOUND(30601, "向量表不存在"),

    /**
     * 向量插入失败
     */
    VECTOR_INSERT_FAILED(30602, "向量插入失败"),

    /**
     * 向量删除失败
     */
    VECTOR_DELETE_FAILED(30603, "向量删除失败"),

    /**
     * 向量维度不匹配
     */
    VECTOR_DIMENSION_MISMATCH(30604, "向量维度不匹配"),

    // ========== 权限相关错误 (30700-30799) ==========
    /**
     * 无权访问知识库
     */
    NO_PERMISSION_TO_ACCESS_KB(30701, "无权访问此知识库"),

    /**
     * 无权删除文档
     */
    NO_PERMISSION_TO_DELETE_DOCUMENT(30702, "无权删除此文档"),

    /**
     * 无权访问会话
     */
    NO_PERMISSION_TO_ACCESS_SESSION(30703, "无权访问此会话"),

    // ========== AI机器人相关错误 (30750-30759) ==========
    /**
     * AI机器人不存在
     */
    ROBOT_NOT_FOUND(30750, "AI机器人不存在"),

    /**
     * AI机器人已禁用
     */
    ROBOT_DISABLED(30751, "AI机器人已禁用"),

    // ========== Agent工作流相关错误 (30760-30779) ==========
    /**
     * Agent工作流不存在
     */
    WORKFLOW_NOT_FOUND(30760, "Agent工作流不存在"),

    /**
     * Agent工作流配置无效
     */
    WORKFLOW_CONFIG_INVALID(30761, "Agent工作流配置无效"),

    /**
     * 工作流模板编码已存在
     */
    WORKFLOW_TEMPLATE_CODE_EXISTS(30762, "工作流模板编码已存在"),

    /**
     * 工作流模板版本不存在
     */
    WORKFLOW_TEMPLATE_VERSION_NOT_FOUND(30763, "工作流模板版本不存在"),

    /**
     * 工作流执行失败
     */
    WORKFLOW_EXECUTION_FAILED(30762, "工作流执行失败"),

    /**
     * 工作流执行记录不存在
     */
    WORKFLOW_EXECUTION_NOT_FOUND(30763, "工作流执行记录不存在"),

    /**
     * 工作流执行超时
     */
    WORKFLOW_EXECUTION_TIMEOUT(30764, "工作流执行超时"),

    // ========== 数据源相关错误 (30800-30899) ==========
    /**
     * 数据源错误
     */
    DATASOURCE_ERROR(30801, "数据源操作失败"),

    /**
     * 租户数据源创建失败
     */
    TENANT_DATASOURCE_CREATE_FAILED(30802, "租户数据源创建失败"),

    /**
     * 租户数据源不存在
     */
    TENANT_DATASOURCE_NOT_FOUND(30803, "租户数据源不存在"),

    /**
     * 租户数据源连接失败
     */
    TENANT_DATASOURCE_CONNECTION_FAILED(30804, "租户数据源连接失败"),

    /**
     * 租户数据源配置无效
     */
    TENANT_DATASOURCE_CONFIG_INVALID(30805, "租户数据源配置无效"),

    /**
     * 租户数据源已存在
     */
    TENANT_DATASOURCE_ALREADY_EXISTS(30806, "租户数据源已存在"),

    /**
     * 租户数据源移除失败
     */
    TENANT_DATASOURCE_REMOVE_FAILED(30807, "租户数据源移除失败"),

    // ========== 系统错误 (30900-30999) ==========
    /**
     * 系统内部错误
     */
    SYSTEM_ERROR(30900, "系统内部错误"),

    /**
     * 配置错误
     */
    CONFIGURATION_ERROR(30901, "系统配置错误"),

    /**
     * 不支持的操作
     */
    OPERATION_NOT_SUPPORTED(30902, "不支持的操作"),

    /**
     * 参数无效
     */
    INVALID_PARAMETER(30903, "参数无效"),

    /**
     * 并发更新失败
     */
    CONCURRENT_UPDATE_FAILED(30904, "并发更新失败，请重试");

    /**
     * 错误码
     */
    private final Integer code;

    /**
     * 错误消息
     */
    private final String message;
}
