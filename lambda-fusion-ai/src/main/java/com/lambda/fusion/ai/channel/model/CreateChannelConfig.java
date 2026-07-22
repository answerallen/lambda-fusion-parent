package com.lambda.fusion.ai.channel.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@Schema(description = "新增通道路由配置")
public class CreateChannelConfig {

    @Schema(description = "通道标识(对应 Channel.channelId())")
    @NotBlank(message = "channelId 不能为空")
    private String channelId;

    @Schema(description = "适配器类型: dingtalk/feishu/wecom")
    @NotBlank(message = "type 不能为空")
    private String type;

    @Schema(description = "默认 agent(app:{appId}:t:{tenantId})")
    private String defaultAgentId;

    @Schema(description = "DM 会话粒度: MAIN/PER_PEER/PER_CHANNEL_PEER/PER_ACCOUNT_CHANNEL_PEER(默认 MAIN)")
    private String dmScope;

    @Schema(description = "绑定规则列表")
    private List<ChannelBindingDto> bindings;

    @Schema(description = "平台凭证(明文，入库前 AES 加密): dingtalk={appKey,appSecret,robotCode,...} 等")
    private Map<String, Object> properties;

    @Schema(description = "是否启用(默认 true)")
    private Boolean enabled = Boolean.TRUE;

    @Schema(description = "备注")
    private String remark;
}
