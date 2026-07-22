package com.lambda.fusion.ai.channel.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@Schema(description = "更新通道路由配置(字段可选)")
public class UpdateChannelConfig {

    @Schema(description = "适配器类型: dingtalk/feishu/wecom(变更会重建 channel)")
    private String type;

    @Schema(description = "默认 agent(app:{appId}:t:{tenantId})")
    private String defaultAgentId;

    @Schema(description = "DM 会话粒度: MAIN/PER_PEER/PER_CHANNEL_PEER/PER_ACCOUNT_CHANNEL_PEER")
    private String dmScope;

    @Schema(description = "绑定规则列表(传则整体覆盖)")
    private List<ChannelBindingDto> bindings;

    @Schema(description = "平台凭证(明文，入库前 AES 加密;传则覆盖)")
    private Map<String, Object> properties;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "备注")
    private String remark;
}
