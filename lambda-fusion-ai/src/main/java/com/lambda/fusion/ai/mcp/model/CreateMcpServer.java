package com.lambda.fusion.ai.mcp.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@Schema(description = "创建 MCP 服务")
public class CreateMcpServer {

    @Schema(description = "服务名称")
    @NotBlank(message = "服务名称不能为空")
    private String name;

    @Schema(description = "传输类型: stdio/sse/http/streamable_http")
    @NotBlank(message = "传输类型不能为空")
    private String transport;

    @Schema(description = "stdio 命令")
    private String command;

    @Schema(description = "stdio 参数列表")
    private List<String> args;

    @Schema(description = "stdio 环境变量")
    private Map<String, String> env;

    @Schema(description = "http/sse 服务地址")
    private String url;

    @Schema(description = "http/sse 请求头")
    private Map<String, String> headers;

    @Schema(description = "是否启用工具")
    private Boolean enableTools = Boolean.TRUE;

    @Schema(description = "超时(毫秒)")
    private Integer timeout;

    @Schema(description = "是否启用")
    private Boolean enabled = Boolean.TRUE;
}
