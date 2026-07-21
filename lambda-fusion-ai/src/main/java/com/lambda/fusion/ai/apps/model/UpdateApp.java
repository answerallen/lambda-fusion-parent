package com.lambda.fusion.ai.apps.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "更新智能应用")
public class UpdateApp {

    @Schema(description = "应用名称")
    private String name;

    @Schema(description = "应用描述")
    private String description;

    @Schema(description = "系统提示词")
    private String systemPrompt;

    @Schema(description = "绑定模型ID")
    private String modelId;

    @Schema(description = "ReAct 最大迭代数")
    private Integer maxIters;

    @Schema(description = "温度")
    private BigDecimal temperature;

    @Schema(description = "应用类型: CHAT|WORKSPACE")
    private String appType;

    @Schema(description = "是否自演化(WORKSPACE 型)")
    private Boolean selfEvolve;

    @Schema(description = "沙箱后端(WORKSPACE 型): HOST|DOCKER|KUBERNETES|E2B|DAYTONA|AGENTRUN")
    private String sandboxBackend;

    @Schema(description = "工具白名单")
    private List<String> toolsAllow;

    @Schema(description = "工具黑名单")
    private List<String> toolsDeny;

    @Schema(description = "MCP 服务ID列表")
    private List<String> mcpServerIds;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
