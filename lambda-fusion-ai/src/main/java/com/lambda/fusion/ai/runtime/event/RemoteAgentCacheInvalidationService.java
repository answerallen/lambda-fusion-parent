package com.lambda.fusion.ai.runtime.event;

/**
 * 跨实例 Agent 缓存失效广播服务（Dubbo）。多实例部署下本地 {@link ConfigChangedEvent} 不跨实例，
 * 各实例既是 provider 又是 consumer，经 Dubbo broadcast 把失效指令扩散到所有实例；远端收到后回放为
 * {@link ConfigChangedEvent#remote} 失效自身 {@code AgentFactory} 缓存。{@code appId} 为空全量失效。
 *
 * @author Jin
 */
public interface RemoteAgentCacheInvalidationService {

    void invalidate(String appId);
}
