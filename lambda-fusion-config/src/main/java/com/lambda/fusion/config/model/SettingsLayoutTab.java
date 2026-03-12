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
@Schema(description = "系统设置布局页签")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class SettingsLayoutTab {
    @Schema(description = "页签ID")
    private String id;

    @Schema(description = "页签标题")
    private String title;

    @Schema(description = "控件列表")
    private List<SettingsLayoutWidget> items;
}
