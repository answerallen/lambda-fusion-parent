package com.lambda.fusion.config.model;

import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseDTO;
import com.lambda.fusion.config.model.entity.ConfigEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.*;

@AutoConverter(target = ConfigEntity.class)
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "配置保存参数")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class SaveConfig extends BaseDTO<ConfigEntity> {

    @NotBlank(message = "应用名称不能为空")
    @Schema(description = "应用名称")
    private String application;

    @NotBlank(message = "配置键不能为空")
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
    private List<ConfigOption> options;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "配置选项")
    public static class ConfigOption {

        @Schema(description = "选项标签")
        private String label;

        @Schema(description = "选项值")
        private String value;

        @Schema(description = "选项描述")
        private String description;
    }
}
