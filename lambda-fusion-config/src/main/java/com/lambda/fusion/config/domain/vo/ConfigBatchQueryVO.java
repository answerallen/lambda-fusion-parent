package com.lambda.fusion.config.domain.vo;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Schema(description = "批量查询配置信息参数")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class ConfigBatchQueryVO {

    @Schema(description = "配置页面编号")
    String configNo;

    @Schema(description = "配置信息编号列表")
    Set<String> arrays;
}
