package com.lambda.fusion.ai.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lambda.fusion.core.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP（Model Context Protocol）Server 配置实体
 * <p>
 * 支持两种传输类型：
 * - STDIO：通过子进程方式本地运行 MCP Server
 * - HTTP_STREAMABLE：通过 HTTP 远程连接 MCP Server
 *
 * @author Jin
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("ai_mcp_server")
@Schema(description = "MCP服务器配置实体")
public class McpServerEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "服务器ID")
    private String id;

    @Schema(description = "服务器名称（唯一，用于标识）")
    private String name;

    @Schema(description = "显示名称")
    private String displayName;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "传输类型：STDIO / HTTP_STREAMABLE")
    private String transportType;

    @Schema(description = "STDIO 模式：启动命令（JSON 数组格式，例如 [\"npm\", \"exec\", \"@mcp/server\"]）")
    private String command;

    @Schema(description = "HTTP 模式：MCP Server URL")
    private String url;

    @Schema(description = "环境变量（JSON 对象格式，例如 {\"KEY\": \"VALUE\"}）")
    private String envVars;

    @Schema(description = "超时时间（秒），默认30秒")
    private Integer timeoutSeconds;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "租户ID")
    private String tenantId;
}
