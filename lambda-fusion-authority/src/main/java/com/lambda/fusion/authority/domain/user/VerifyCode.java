package com.lambda.fusion.authority.domain.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Schema(description = "短信验证码信息")
public class VerifyCode {

    @Schema(description = "id")
    private String id;

    @Schema(description = "短信验证码可重发秒")
    private Integer resendSeconds;

    @Schema(description = "短信验证码有效分钟")
    private Integer validMinutes;
}
