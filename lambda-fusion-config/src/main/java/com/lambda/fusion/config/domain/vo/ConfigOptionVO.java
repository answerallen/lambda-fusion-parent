package com.lambda.fusion.config.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Schema(description = "选项信息")
public class ConfigOptionVO {

    @NotEmpty
    private String id;

    @NotEmpty
    @Size(max = 32)
    @Schema(description = "选项信息名称")
    private String name;

    @NotEmpty
    @Size(max = 120)
    @Schema(description = "选项信息值")
    @JsonProperty("value")
    private String value;

    @Schema(description = "选项信息描述")
    private String description;
}
