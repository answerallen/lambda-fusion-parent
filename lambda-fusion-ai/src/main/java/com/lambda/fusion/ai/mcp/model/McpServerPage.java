package com.lambda.fusion.ai.mcp.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.lambda.fusion.ai.mcp.model.entity.McpServerEntity;
import com.lambda.fusion.core.pagination.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "MCP 服务分页查询参数")
public class McpServerPage extends PageQuery<McpServerEntity> {

    @Schema(description = "服务名称，支持模糊查询")
    private String name;

    @Schema(description = "传输类型")
    private String transport;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Override
    public LambdaQueryWrapper<McpServerEntity> getLambdaQueryWrapper() {
        LambdaQueryWrapper<McpServerEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(name), McpServerEntity::getName, name);
        wrapper.eq(StringUtils.isNotBlank(transport), McpServerEntity::getTransport, transport);
        wrapper.eq(enabled != null, McpServerEntity::getEnabled, enabled);
        wrapper.orderByDesc(McpServerEntity::getCreatedAt);
        return wrapper;
    }
}
