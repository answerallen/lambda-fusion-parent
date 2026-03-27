package com.lambda.fusion.ai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.fusion.ai.model.Robot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * AI机器人实体封装类
 *
 * @author Jin
 */
@AutoConverter(target = Robot.class)
@Data
@TableName("ai_robot")
@Schema(description = "AI机器人实体")
public class RobotEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "对外暴露的唯一机器人标识")
    private String robotId;

    @Schema(description = "机器人名称")
    private String name;

    @Schema(description = "机器人头像")
    private String avatar;

    @Schema(description = "机器人职能描述")
    private String description;

    @Schema(description = "绑定的LLM模型ID")
    private Long llmModelId;

    @Schema(description = "系统设定人设与初始提示词")
    private String systemPrompt;

    @Schema(description = "关联的知识库ID")
    private Long kbId;

    @Schema(description = "关联的主体工作流配置ID (如基于多Agent)")
    private Long workflowId;

    @Schema(description = "租户隔离ID")
    private Long tenantId;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "是否系统全局公开")
    private Boolean isPublic;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
