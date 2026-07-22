package com.lambda.fusion.ai.apps.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "创建智能应用")
public class CreateApp {

    @Schema(description = "应用名称")
    @NotBlank(message = "应用名称不能为空")
    private String name;

    @Schema(description = "应用描述")
    private String description;

    @Schema(description = "系统提示词")
    private String systemPrompt;

    @Schema(description = "绑定模型ID")
    @NotBlank(message = "模型ID不能为空")
    private String modelId;

    @Schema(description = "ReAct 最大迭代数")
    private Integer maxIters;

    @Schema(description = "温度")
    private BigDecimal temperature;

    @Schema(description = "应用类型: CHAT|WORKSPACE")
    private String appType = "CHAT";

    @Schema(description = "是否自演化(WORKSPACE 型)")
    private Boolean selfEvolve = Boolean.FALSE;

    @Schema(description = "沙箱后端(WORKSPACE 型): HOST|DOCKER|KUBERNETES|E2B|DAYTONA|AGENTRUN")
    private String sandboxBackend = "HOST";

    @Schema(description = "受众: B|C|ALL")
    private String audience = "ALL";

    @Schema(description = "工具白名单")
    private List<String> toolsAllow;

    @Schema(description = "工具黑名单")
    private List<String> toolsDeny;

    @Schema(description = "MCP 服务ID列表")
    private List<String> mcpServerIds;

    @Schema(description = "知识库ID列表(对话时自动检索注入)")
    private List<String> knowledgeBaseIds;

    @Schema(description = "技能白名单(技能名;仅 WORKSPACE 型生效)")
    private List<String> skillsAllow;

    @Schema(description = "技能黑名单(技能名;仅 WORKSPACE 型生效)")
    private List<String> skillsDeny;

    @Schema(description = "是否启用")
    private Boolean enabled = Boolean.TRUE;
}
