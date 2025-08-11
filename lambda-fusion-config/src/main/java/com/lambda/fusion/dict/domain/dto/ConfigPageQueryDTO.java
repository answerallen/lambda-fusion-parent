package com.lambda.fusion.dict.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Schema(description = "配置分页查询参数")
public class ConfigPageQueryDTO {

    @Schema(description = "配置信息键")
    private String key;

    @Schema(description = "配置名称")
    private String name;

    @Schema(description = "模块名称")
    private String application;

    @Schema(description = "是否只查询租户配置项, 0:否, 1:是")
    private int tenantOnly = 0;
}
