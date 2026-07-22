package com.lambda.fusion.ai.skill.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Data;

@Data
@Schema(description = "更新平台技能(字段可选)")
public class UpdateSkill {

    @Schema(description = "描述")
    private String description;

    @Schema(description = "版本")
    private String version;

    @Schema(description = "SKILL.md 内容")
    private String markdown;

    @Schema(description = "资源 map(name->content;传则覆盖)")
    private Map<String, String> resources;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "备注")
    private String remark;
}
