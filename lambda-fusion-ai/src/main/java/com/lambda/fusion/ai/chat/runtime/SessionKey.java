package com.lambda.fusion.ai.chat.runtime;

/**
 * 会话源流尾链的会话键：{@code (tenantId, userId, sessionId)}。
 *
 * <p>与 AgentScope 状态槽身份一致；不使用 appId 或 Workspace Agent ID，保证同一业务会话的相邻源流按排空
 * 顺序串行，而不同用户/不同会话互不阻塞。
 *
 * @author Jin
 */
record SessionKey(String tenantId, String userId, String sessionId) {}
