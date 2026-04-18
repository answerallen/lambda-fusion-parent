package com.lambda.fusion.ai.model;

import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseVO;
import com.lambda.fusion.ai.model.entity.McpServerEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP Server 配置 VO（对外展示模型）
 *
 * @author Jin
 */
@EqualsAndHashCode(callSuper = true)
@AutoConverter(target = McpServerEntity.class, isReverse = true)
@Data
@Schema(description = "MCP服务器信息")
public class McpServer extends BaseVO<McpServerEntity> {

    @Schema(description = "服务器ID")
    private String id;

    @Schema(description = "服务器名称")
    private String name;

    @Schema(description = "显示名称")
    private String displayName;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "传输类型：STDIO / HTTP_STREAMABLE")
    private String transportType;

    @Schema(description = "STDIO 启动命令（JSON 数组字符串）")
    private String command;

    @Schema(description = "HTTP Server URL")
    private String url;

    @Schema(description = "环境变量（JSON 对象字符串）")
    private String envVars;

    @Schema(description = "超时时间（秒）")
    private Integer timeoutSeconds;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
