package com.lambda.fusion.ai.runtime.workspace;

import com.lambda.fusion.ai.AiConstants.WorkspaceStorageType;
import io.agentscope.harness.agent.DistributedStore;

/**
 * Workspace 分布式存储提供者。扩展按后端条件装配，系统配置只选择其中一个实现。
 *
 * @author Jin
 */
public interface WorkspaceDistributedStoreProvider {

    WorkspaceStorageType type();

    DistributedStore create();
}
