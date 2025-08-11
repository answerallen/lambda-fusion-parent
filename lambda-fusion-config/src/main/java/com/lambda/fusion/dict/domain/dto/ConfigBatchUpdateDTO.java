package com.lambda.fusion.dict.domain.dto;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "配置批量更新参数")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class ConfigBatchUpdateDTO {

    @NotBlank(message = "应用名称不能为空")
    @Schema(description = "应用名称")
    private String application;

    @NotEmpty(message = "更新配置列表不能为空")
    @Schema(description = "待更新的配置项列表")
    private List<ConfigUpdateItem> configs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "配置更新项")
    public static class ConfigUpdateItem {

        @NotBlank(message = "配置ID不能为空")
        @Schema(description = "配置ID")
        private String id;

        @Schema(description = "配置值")
        private String value;

        @Schema(description = "配置描述")
        private String description;
    }
}
