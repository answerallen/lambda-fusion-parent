package com.lambda.fusion.ai.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新 AI机器人 DTO
 */
@Data
@Schema(description = "更新AI机器人请求体")
public class UpdateRobot {

    @NotNull(message = "ID不能为空")
    @Schema(description = "自增主键ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "机器人名称")
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

    @Schema(description = "开启状态")
    private Boolean enabled;

    @Schema(description = "是否全局公开")
    private Boolean isPublic;
}
