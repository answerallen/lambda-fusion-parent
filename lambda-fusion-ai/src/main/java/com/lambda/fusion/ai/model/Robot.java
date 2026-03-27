package com.lambda.fusion.ai.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * AI机器人返回对象 DTO
 */
@Data
@Schema(description = "AI机器人返回信息")
public class Robot {

    private Long id;

    @Schema(description = "机器人对外标识")
    private String robotId;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "LLM模型ID")
    private Long llmModelId;

    @Schema(description = "系统提示词")
    private String systemPrompt;

    @Schema(description = "关联知识库ID")
    private Long kbId;

    @Schema(description = "关联工作流ID")
    private Long workflowId;

    @Schema(description = "所属租户ID")
    private Long tenantId;

    @Schema(description = "开启状态")
    private Boolean enabled;

    @Schema(description = "系统公开状态")
    private Boolean isPublic;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
