package com.lambda.fusion.ai.channel.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.lambda.fusion.ai.channel.model.ChannelBindingDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 通道路由配置实体：一个 {@code channelId} 对应一条记录，承载默认 agent、DM 粒度与绑定规则。
 *
 * <p>平台级能力配置（无 tenant_id），对标 {@code ai_mcp_server}。
 *
 * @author Jin
 */
@Data
@TableName(value = "ai_channel_config", autoResultMap = true)
@Schema(description = "通道路由配置")
public class ChannelConfigEntity {

    @TableId("id")
    @Schema(description = "主键")
    private String id;

    @TableField("channel_id")
    @Schema(description = "通道标识(对应 Channel.channelId())")
    private String channelId;

    @TableField("type")
    @Schema(description = "适配器类型: dingtalk/feishu/wecom")
    private String type;

    @TableField("default_agent_id")
    @Schema(description = "默认 agent(app:{appId}:t:{tenantId})")
    private String defaultAgentId;

    @TableField("dm_scope")
    @Schema(description = "DM 会话粒度: MAIN/PER_PEER/PER_CHANNEL_PEER/PER_ACCOUNT_CHANNEL_PEER")
    private String dmScope;

    @TableField(value = "bindings", typeHandler = JacksonTypeHandler.class)
    @Schema(description = "绑定规则列表")
    private List<ChannelBindingDto> bindings;

    @TableField("properties_encrypted")
    @Schema(description = "平台凭证 JSON 密文(AES-GCM)")
    private String propertiesEncrypted;

    @TableField("enabled")
    @Schema(description = "是否启用")
    private Boolean enabled;

    @TableField("remark")
    @Schema(description = "备注")
    private String remark;

    @TableField("created_at")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
