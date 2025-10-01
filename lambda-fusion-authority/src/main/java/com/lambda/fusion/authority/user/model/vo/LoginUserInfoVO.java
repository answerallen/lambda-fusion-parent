package com.lambda.fusion.authority.user.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Schema(description = "登陆用户信息")
@EqualsAndHashCode(callSuper = true)
public class LoginUserInfoVO extends MutableUserVO {
    @Schema(description = "当前SessionId")
    private String currentSesssionId;
}
