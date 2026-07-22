package com.lambda.fusion.ai.channel.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.lambda.fusion.ai.channel.model.entity.ChannelConfigEntity;
import com.lambda.fusion.core.pagination.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "通道路由配置分页查询参数")
public class ChannelConfigPage extends PageQuery<ChannelConfigEntity> {

    @Schema(description = "通道标识，支持模糊查询")
    private String channelId;

    @Schema(description = "默认 agent，支持模糊查询")
    private String defaultAgentId;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Override
    public LambdaQueryWrapper<ChannelConfigEntity> getLambdaQueryWrapper() {
        LambdaQueryWrapper<ChannelConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(channelId), ChannelConfigEntity::getChannelId, channelId);
        wrapper.like(StringUtils.isNotBlank(defaultAgentId), ChannelConfigEntity::getDefaultAgentId, defaultAgentId);
        wrapper.eq(enabled != null, ChannelConfigEntity::getEnabled, enabled);
        wrapper.orderByDesc(ChannelConfigEntity::getCreatedAt);
        return wrapper;
    }
}
