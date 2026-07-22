package com.lambda.fusion.ai.mcp.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@TableName(value = "ai_mcp_server", autoResultMap = true)
@Schema(description = "MCP 服务配置")
public class McpServerEntity {

    @TableId("id")
    @Schema(description = "主键")
    private String id;

    @TableField("name")
    @Schema(description = "服务名称")
    private String name;

    @TableField("transport")
    @Schema(description = "传输类型: stdio/sse/http/streamable_http")
    private String transport;

    @TableField("command")
    @Schema(description = "stdio 命令")
    private String command;

    @TableField(value = "args", typeHandler = JacksonTypeHandler.class)
    @Schema(description = "stdio 参数列表")
    private List<String> args;

    @TableField(value = "env", typeHandler = JacksonTypeHandler.class)
    @Schema(description = "stdio 环境变量")
    private Map<String, String> env;

    @TableField("url")
    @Schema(description = "http/sse 服务地址")
    private String url;

    @TableField(value = "headers", typeHandler = JacksonTypeHandler.class)
    @Schema(description = "http/sse 请求头")
    private Map<String, String> headers;

    @TableField("enable_tools")
    @Schema(description = "是否启用工具")
    private Boolean enableTools;

    @TableField("timeout")
    @Schema(description = "超时(毫秒)")
    private Integer timeout;

    @TableField("enabled")
    @Schema(description = "是否启用")
    private Boolean enabled;

    @TableField("created_at")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
