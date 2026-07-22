package com.lambda.fusion.ai.skill.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

/**
 * 平台技能市场实体：一个 {@code name} 对应一条 SKILL.md 内容 + 资源。
 *
 * <p>平台级能力（无 tenant_id），对标 {@code ai_mcp_server}。运行时由 {@code DbSkillRepository}
 * 读取为 harness {@code AgentSkill}。
 *
 * @author Jin
 */
@Data
@TableName(value = "ai_skill", autoResultMap = true)
@Schema(description = "平台技能")
public class SkillEntity {

    @TableId("id")
    @Schema(description = "主键")
    private String id;

    @TableField("name")
    @Schema(description = "技能名(运行时 key)")
    private String name;

    @TableField("description")
    @Schema(description = "描述")
    private String description;

    @TableField("version")
    @Schema(description = "版本(管理展示)")
    private String version;

    @TableField("markdown")
    @Schema(description = "SKILL.md 内容(AgentSkill.skillContent)")
    private String markdown;

    @TableField(value = "resources", typeHandler = JacksonTypeHandler.class)
    @Schema(description = "资源 map(name->content)")
    private Map<String, String> resources;

    @TableField("enabled")
    @Schema(description = "是否启用")
    private Boolean enabled;

    @TableField("remark")
    @Schema(description = "备注")
    private String remark;

    @TableField("created_at")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
