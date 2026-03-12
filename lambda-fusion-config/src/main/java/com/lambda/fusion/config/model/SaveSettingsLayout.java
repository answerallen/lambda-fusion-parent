package com.lambda.fusion.config.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "保存系统设置布局参数")
public class SaveSettingsLayout {
    @NotBlank(message = "应用名称不能为空")
    @Schema(description = "应用名称")
    private String application;

    @NotNull(message = "布局数据不能为空")
    @Valid
    @Schema(description = "布局数据")
    private SettingsLayout layout;
}
