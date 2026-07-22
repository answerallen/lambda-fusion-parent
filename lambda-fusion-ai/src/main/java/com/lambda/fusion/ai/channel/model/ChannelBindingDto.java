package com.lambda.fusion.ai.channel.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.Data;

/**
 * 通道路由绑定规则，对齐 AgentScope {@code ChannelBinding}（9 字段一一对应）。
 *
 * <p>一条规则把一个入站维度（peer/guild/team/account/channel 之一）绑定到目标 agent。
 * 序列化为 JSON 存入 {@code ai_channel_config.bindings}。
 *
 * @author Jin
 */
@Data
@Schema(description = "通道路由绑定规则(对齐 AgentScope ChannelBinding)")
public class ChannelBindingDto {

    @Schema(description = "目标 agent(app:{appId}:t:{tenantId})")
    private String agentId;

    @Schema(description = "对等端标识(DM/群/频道)")
    private String peer;

    @Schema(description = "父对等端(线程场景)")
    private String parentPeer;

    @Schema(description = "guild/服务器标识")
    private String guild;

    @Schema(description = "角色集合(guild 维度匹配)")
    private Set<String> roles;

    @Schema(description = "team 标识")
    private String team;

    @Schema(description = "account 标识")
    private String account;

    @Schema(description = "channel 标识")
    private String channel;

    @Schema(description = "会话粒度: MAIN/PER_PEER/PER_CHANNEL_PEER/PER_ACCOUNT_CHANNEL_PEER")
    private String sessionScope;
}
