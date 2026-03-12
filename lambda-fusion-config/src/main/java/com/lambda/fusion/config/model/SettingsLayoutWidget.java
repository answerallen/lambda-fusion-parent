package com.lambda.fusion.config.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "系统设置布局控件")
public class SettingsLayoutWidget {
    @Schema(description = "配置ID")
    private String configId;

    @Schema(description = "排序")
    private Integer orderNo;
}
