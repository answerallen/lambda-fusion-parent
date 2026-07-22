package com.lambda.fusion.ai.runtime.event;

/**
 * AI 配置变更事件，用于失效 {@code AiAgentFactory} 中缓存的 {@code HarnessAgent}。
 *
 * <p>{@code appId} 为 {@code null} 表示全量失效（如模型/提供方变更，影响所有应用）。
 *
 * @author Jin
 */
public record ConfigChangedEvent(String appId) {

    public static ConfigChangedEvent app(String appId) {
        return new ConfigChangedEvent(appId);
    }

    public static ConfigChangedEvent all() {
        return new ConfigChangedEvent(null);
    }
}
