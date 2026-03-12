package com.lambda.fusion.config.model;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "系统设置布局")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class SettingsLayout {
    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "应用名称")
    private String application;

    @Schema(description = "页签列表")
    private List<SettingsLayoutTab> tabs;
}
