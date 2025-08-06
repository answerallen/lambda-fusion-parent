package com.lambda.fusion.auth.apitoken.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;


@Data
@Schema(description="Api Token授权信息")

public class UpdateTokenVO {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    @NotEmpty(message = "ID 不能为空")
    private String id;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "IP白名单，多个用','分割")
    private String ipWhiteList;

    @Schema(description = "是否可用 1 启用")
    @NotNull(message = " 启用状态不能为空")
    private Integer enabled;

    @Schema(description = "失效时间")
    @NotNull(message = " 失效时间不能为空")
    private Date expirationTime;
}
