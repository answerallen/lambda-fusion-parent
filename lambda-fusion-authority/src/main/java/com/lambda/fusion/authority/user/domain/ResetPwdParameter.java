package com.lambda.fusion.authority.user.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Schema(description = "重置密码")
public class ResetPwdParameter {

    @Schema(description = "用户名")
    private String username;

    @Schema(required = true, description = "旧密码")
    private String oldPassword;

    @Schema(required = true, description = "新密码")
    private String newPassword;
}
