package com.lambda.fusion.ai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ai_agent_workflow")
@Schema(description = "Agent工作流配置实体")
public class WorkflowEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    @Schema(description = "工作流名称")
    private String name;

    @Schema(description = "工作流描述")
    private String description;

    @Schema(description = "图的完整 JSON 配置，用于反序列化给 GraphDefinition")
    private String graphJson;

    @Schema(description = "创建者用户ID")
    private String ownerUserId;

    @Schema(description = "是否公开")
    private Boolean isPublic;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "执行次数")
    private Long executionCount;

    @Schema(description = "成功次数")
    private Long successCount;

    @Schema(description = "平均执行时长(ms)")
    private Integer avgDurationMs;

    @Schema(description = "租户隔离ID")
    private String tenantId;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @Schema(description = "创建人ID")
    private String createdBy;

    @Schema(description = "更新人ID")
    private String updatedBy;
}
