package com.lambda.fusion.ai.runtime.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

/**
 * 监听本地 {@link ConfigChangedEvent}（{@code remote=false}），经 Dubbo broadcast 广播到所有实例。
 * 由 Dubbo 在 classpath 且 {@code invalidation-broadcast} 开启（默认开）时装配，否则退化为单实例；
 * {@code remote=true} 的远端回放跳过防回环。注入的 {@link RemoteAgentCacheInvalidationService} 为
 * {@code cluster="broadcast"} 引用，一次调用扩散所有 provider（含自身，幂等）；{@code check=false} 不阻塞本地。
 *
 * @author Jin
 */
@Slf4j
@RequiredArgsConstructor
public class DubboConfigInvalidationBroadcaster {

    private final RemoteAgentCacheInvalidationService remote;

    @EventListener
    public void onConfigChanged(ConfigChangedEvent event) {
        if (event.remote()) {
            return;
        }
        try {
            remote.invalidate(event.appId());
        } catch (Exception e) {
            log.warn("跨实例 Agent 缓存失效广播失败(appId={}): {}", event.appId(), e.getMessage());
        }
    }
}
