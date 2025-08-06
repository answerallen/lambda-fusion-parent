package com.lambda.fusion.configs.domain.dto;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@SuppressFBWarnings("EI_EXPOSE_REP")
@Schema(description = "配置ID批量查询参数")
public class ConfigQueryDTO {

    @Schema(description = "应用名称")
    String application;

    @Schema(description = "配置ID列表")
    List<String> ids;
}
