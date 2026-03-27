package com.lambda.fusion.ai.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建 AI机器人 DTO
 */
@Data
@Schema(description = "创建AI机器人请求体")
public class CreateRobot {

    @NotBlank(message = "机器人名称不能为空")
    @Schema(description = "机器人名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "机器人头像")
    private String avatar;

    @Schema(description = "职能描述")
    private String description;

    @Schema(description = "关联长文本提示词")
    private String systemPrompt;

    @Schema(description = "LLM模型ID")
    private Long llmModelId;

    @Schema(description = "知识库ID")
    private Long kbId;

    @Schema(description = "工作流ID")
    private Long workflowId;

    @Schema(description = "开启状态", defaultValue = "true")
    private Boolean enabled = true;

    @Schema(description = "是否全局公开", defaultValue = "false")
    private Boolean isPublic = false;
}
