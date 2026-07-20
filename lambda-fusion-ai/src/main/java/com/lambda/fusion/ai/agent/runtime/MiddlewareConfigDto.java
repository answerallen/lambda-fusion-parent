package com.lambda.fusion.ai.agent.runtime;

import java.util.Map;

/**
 * 横切中间件配置（{@code ai_app.middleware_config} JSON 数组的单个元素）。{@code type} 标识中间件
 * 类型（如 {@code rate_limit}），{@code enabled} 控制开关，{@code params} 传构造参数。
 * {@link MiddlewareFactory} 按 type 构造对应 {@code MiddlewareBase}。
 *
 * <p>注：RAG 的 {@link RagMiddleware} 按 {@code ragMode} 装配，不归此处；此处装横切能力（限流等）。
 *
 * @author Jin
 */
public record MiddlewareConfigDto(String type, Boolean enabled, Map<String, Object> params) {}
