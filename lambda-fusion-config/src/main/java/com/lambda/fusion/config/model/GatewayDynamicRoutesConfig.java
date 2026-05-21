package com.lambda.fusion.config.model;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "网关动态路由配置")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class GatewayDynamicRoutesConfig {

    @Schema(description = "应用名称")
    private String application;

    @NotBlank(message = "路由配置不能为空")
    @Schema(description = "Spring Cloud Gateway 路由 JSON 数组")
    private String routesJson;

    @Schema(description = "配置名称")
    private String name;

    @Schema(description = "配置描述")
    private String description;
}
