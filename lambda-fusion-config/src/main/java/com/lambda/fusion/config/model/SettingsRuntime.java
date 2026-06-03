package com.lambda.fusion.config.model;

import com.lambda.fusion.config.model.entity.ConfigEntity;
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
@Schema(description = "系统设置运行时数据")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class SettingsRuntime {
    @Schema(description = "应用名称")
    private String application;

    @Schema(description = "页面布局")
    private SettingsLayout layout;

    @Schema(description = "配置定义")
    private List<ConfigEntity> configs;
}
