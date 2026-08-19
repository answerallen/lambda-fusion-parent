package com.lambda.fusion.ai.runtime.event;

/**
 * AI 配置变更事件，用于失效 {@code AgentFactory} 中缓存的 {@code HarnessAgent}。
 * {@code appId} 为空表示全量失效（如模型/提供方变更影响所有应用）；{@code remote} 标识来源：
 * 本地变更（false）由 {@code DubboConfigInvalidationBroadcaster} 广播到其他实例，远端收到后
 * 回放为 true 仅失效本地缓存不再外播（防回环），{@code AgentFactory} 均失效对应缓存。
 *
 * @author Jin
 */
public record ConfigChangedEvent(String appId, boolean remote) {

    /** 本地配置变更：失效本地缓存，并由广播器（若启用）扩散到其他实例。 */
    public static ConfigChangedEvent app(String appId) {
        return new ConfigChangedEvent(appId, false);
    }

    /** 本地全量变更：appId=null 表示全量失效。 */
    public static ConfigChangedEvent all() {
        return new ConfigChangedEvent(null, false);
    }

    /** 远端广播回放：仅失效本地缓存，不再外播（防回环）。 */
    public static ConfigChangedEvent remote(String appId) {
        return new ConfigChangedEvent(appId, true);
    }
}
