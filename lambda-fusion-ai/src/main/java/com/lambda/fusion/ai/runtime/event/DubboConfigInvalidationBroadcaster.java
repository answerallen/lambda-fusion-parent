package com.lambda.fusion.ai.runtime.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

/**
 * 监听本地 {@link ConfigChangedEvent}（{@code remote=false}），经 Dubbo broadcast 广播到所有实例。
 *
 * <p>由 {@code AiConfigure.DubboInvalidationConfiguration} 在 Dubbo 在 classpath 且
 * {@code lambda.fusion.ai.cluster.invalidation-broadcast} 开启（默认开）时装配；否则不注册，
 * 退化为单实例本地事件。{@code remote=true} 的事件（远端回放）跳过，防回环。
 *
 * <p>注入的 {@link RemoteAgentCacheInvalidationService} 为 {@code cluster="broadcast"} 的 Dubbo 引用：
 * 一次调用扩散到所有注册 provider（含自身，幂等无害）；{@code check=false} 避免其他实例未就绪时阻塞本地。
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
