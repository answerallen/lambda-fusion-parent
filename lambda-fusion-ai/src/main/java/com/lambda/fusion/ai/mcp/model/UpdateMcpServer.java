package com.lambda.fusion.ai.mcp.model;

import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseDTO;
import com.lambda.fusion.ai.mcp.model.entity.McpServerEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新 MCP Server 请求
 *
 * @author Jin
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AutoConverter(target = McpServerEntity.class)
@Schema(description = "更新MCP服务器请求")
public class UpdateMcpServer extends BaseDTO<McpServerEntity> {

    @Size(max = 128, message = "显示名称最长128字符")
    @Schema(description = "显示名称")
    private String displayName;

    @Schema(description = "描述")
    private String description;

    @Pattern(regexp = "STDIO|HTTP_STREAMABLE", message = "传输类型必须为 STDIO 或 HTTP_STREAMABLE")
    @Schema(description = "传输类型：STDIO / HTTP_STREAMABLE")
    private String transportType;

    @Schema(description = "STDIO 启动命令（JSON数组格式）")
    private String command;

    @Schema(description = "HTTP Server URL")
    private String url;

    @Schema(description = "环境变量（JSON对象格式）")
    private String envVars;

    @Schema(description = "超时时间（秒）")
    private Integer timeoutSeconds;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
