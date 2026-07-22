package com.lambda.fusion.ai.channel;

import io.agentscope.harness.agent.gateway.channel.Channel;
import io.agentscope.harness.agent.gateway.channel.ChannelConfig;
import java.util.Map;

/**
 * 渠道适配器工厂 SPI：按 {@code type} 构造一个 {@link Channel}。
 *
 * <p>每个平台适配器一个实现，{@code @ConditionalOnClass} 条件装配（扩展不在 classpath 时不注册）。
 * 实现委托给 AgentScope 适配器的 {@code XxxChannel.fromProperties(channelId, routing, properties)}。
 *
 * @author Jin
 */
public interface ChannelFactoryProvider {

    // 适配器类型标识（对齐 {@code XxxChannel.TYPE}）：dingtalk/feishu/wecom
    String type();

    // 按 channelId + 路由 + 平台凭证构造一个 {@link Channel}
    Channel create(String channelId, ChannelConfig routing, Map<String, Object> properties);
}
