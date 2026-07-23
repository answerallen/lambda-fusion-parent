package com.lambda.fusion.ai.subagent.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "创建子代理")
public class CreateSubAgent {

    @Schema(description = "子代理名(运行时 key/agent_id)")
    @NotBlank(message = "子代理名不能为空")
    private String name;

    @Schema(description = "能力描述(主 agent 路由唯一依据,写清何时应调度该子代理)")
    @NotBlank(message = "能力描述不能为空")
    private String description;

    @Schema(description = "子代理系统提示词")
    @NotBlank(message = "系统提示词不能为空")
    private String prompt;

    @Schema(description = "绑定模型ID(空=继承主 agent 模型)")
    private String modelId;

    @Schema(description = "最大 ReAct 迭代数(空=harness 默认)")
    private Integer steps;

    @Schema(description = "温度")
    private BigDecimal temperature;

    @Schema(description = "Top-P")
    private BigDecimal topP;

    @Schema(description = "工具白名单(空=全继承父工具)")
    private List<String> toolsAllow;

    @Schema(description = "技能白名单(空=全继承)")
    private List<String> skillsAllow;

    @Schema(description = "工作区模式: ISOLATED|SHARED")
    private String workspaceMode = "ISOLATED";

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "是否启用")
    private Boolean enabled = Boolean.TRUE;
}
