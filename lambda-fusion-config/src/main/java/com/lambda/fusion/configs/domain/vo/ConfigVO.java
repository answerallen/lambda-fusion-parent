package com.lambda.fusion.configs.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Schema(description = "配置信息")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class ConfigVO {

    @Schema(description = "配置信息键")
    private String key;

    @Schema(description = "配置信息值")
    private String value;

    @Schema(description = "配置信息名称")
    private String name;

    @Schema(description = "配置信息类型,可选值：0-开关,1-枚举值,2-字符串,3-数值", example = "0", allowableValues = "0,1,2,3")
    private Integer type;

    @Schema(description = "配置信息描述")
    private String description;

    @Valid
    @Schema(description = "配置选项信息")
    private List<ConfigOptionVO> options;

    @Schema(description = "模块名称")
    @JsonProperty(value = "module")
    private String application;
}
