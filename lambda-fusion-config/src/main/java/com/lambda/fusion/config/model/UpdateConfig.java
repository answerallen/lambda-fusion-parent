package com.lambda.fusion.config.model;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "配置更新参数")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class UpdateConfig {

    @NotBlank(message = "配置ID不能为空")
    @Schema(description = "配置ID")
    private String id;

    @Schema(description = "配置键")
    private String key;

    @Schema(description = "配置值")
    private String value;

    @Schema(description = "配置名称")
    private String name;

    @Schema(description = "配置描述")
    private String description;

    @Schema(description = "配置类型")
    private Integer type;

    @Schema(description = "配置选项列表")
    private List<SaveConfig.ConfigOption> options;
}
