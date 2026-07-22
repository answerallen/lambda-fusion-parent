package com.lambda.fusion.ai.runtime.state;

import com.lambda.fusion.ai.AiConstants.StateStoreType;
import io.agentscope.core.state.AgentStateStore;

/**
 * Agent 状态存储提供者：为指定后端构建 {@link AgentStateStore}。
 *
 * <p>每个后端一个实现，按 {@code @ConditionalOnClass} 条件装配（扩展不在 classpath 时不注册）。
 * 存储实例跨 agent 共享（按 {@code (userId, sessionId)} 隔离，无跨应用冲突）。
 *
 * @author Jin
 */
public interface StateStoreProvider {

    StateStoreType type();

    AgentStateStore create();
}
