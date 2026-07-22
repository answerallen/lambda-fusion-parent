package com.lambda.fusion.ai.skill.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Data;

@Data
@Schema(description = "技能视图")
public class SkillView {

    @Schema(description = "技能名")
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "SKILL.md 内容")
    private String markdown;

    @Schema(description = "资源 map(name->content)")
    private Map<String, String> resources;
}
