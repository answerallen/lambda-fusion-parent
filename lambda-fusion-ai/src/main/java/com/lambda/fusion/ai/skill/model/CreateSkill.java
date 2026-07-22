package com.lambda.fusion.ai.skill.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.Data;

@Data
@Schema(description = "新增平台技能")
public class CreateSkill {

    @Schema(description = "技能名(运行时 key)")
    @NotBlank(message = "技能名不能为空")
    private String name;

    @Schema(description = "描述")
    @NotBlank(message = "描述不能为空")
    private String description;

    @Schema(description = "版本")
    private String version;

    @Schema(description = "SKILL.md 内容")
    @NotBlank(message = "SKILL.md 内容不能为空")
    private String markdown;

    @Schema(description = "资源 map(name->content)")
    private Map<String, String> resources;

    @Schema(description = "是否启用(默认 true)")
    private Boolean enabled = Boolean.TRUE;

    @Schema(description = "备注")
    private String remark;
}
