package com.lambda.fusion.ai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lambda.fusion.core.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("ai_agent_workflow")
@Schema(description = "Agent工作流配置实体")
public class WorkflowEntity  extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
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

}
