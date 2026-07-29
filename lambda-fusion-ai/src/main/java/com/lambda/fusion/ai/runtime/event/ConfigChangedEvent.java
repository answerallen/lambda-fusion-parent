package com.lambda.fusion.ai.runtime.event;

/**
 * AI 配置变更事件，用于失效 {@code AgentFactory} 中缓存的 {@code HarnessAgent}。
 *
 * <p>{@code appId} 为 {@code null} 表示全量失效（如模型/提供方变更，影响所有应用）。
 *
 * <p>{@code remote} 标识来源：本地配置变更（{@code false}）由 {@code DubboConfigInvalidationBroadcaster}
 * 经 Dubbo 广播到其他实例；远端实例收到后回放为 {@code true}，仅失效本地缓存不再外播（防回环）。
 * {@code AgentFactory} 不区分来源，均失效对应缓存。
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
