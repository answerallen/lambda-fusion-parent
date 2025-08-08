package com.lambda.fusion.config.domain.dto;

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
@Schema(description = "配置列表查询参数")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class ConfigListQueryDTO {

    @Schema(description = "配置键，支持右侧模糊查询")
    private String key;

    @Schema(description = "配置ID列表")
    private List<String> ids;

    @Schema(description = "配置ID字符串，逗号分隔")
    private String idsString;

    @Schema(description = "配置键列表")
    private List<String> keys;

    @Schema(description = "配置键字符串，逗号分隔")
    private String keysString;

    @Schema(description = "应用名称")
    private String application;
}
