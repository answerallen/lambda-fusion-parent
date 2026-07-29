package com.lambda.fusion.ai.runtime.event;

/**
 * 跨实例 Agent 缓存失效广播服务（Dubbo）。
 *
 * <p>多实例部署下，本地 {@link ConfigChangedEvent} 不跨实例；每个实例既是 provider 又是 consumer，
 * 通过 Dubbo broadcast 集群把失效指令扩散到所有实例。远端收到后回放为
 * {@link ConfigChangedEvent#remote} 事件，失效自身 {@code AgentFactory} 缓存。
 *
 * <p>{@code appId} 为 {@code null} 表示全量失效；否则失效该 app（所有 tenant）。
 *
 * @author Jin
 */
public interface RemoteAgentCacheInvalidationService {

    void invalidate(String appId);
}
