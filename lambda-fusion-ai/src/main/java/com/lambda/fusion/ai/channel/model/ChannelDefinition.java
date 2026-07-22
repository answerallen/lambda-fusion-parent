package com.lambda.fusion.ai.channel.model;

import io.agentscope.harness.agent.gateway.channel.ChannelConfig;
import java.util.Map;

/**
 * 渠道构造视图：channelId + 类型 + 平台凭证(已解密) + 路由配置，供 {@code ChannelBootstrap} 构造 channel。
 *
 * @param channelId 通道标识
 * @param type 适配器类型（dingtalk/feishu/wecom）
 * @param properties 平台凭证明文 map
 * @param routing harness 路由配置
 */
public record ChannelDefinition(String channelId, String type, Map<String, Object> properties, ChannelConfig routing) {}
