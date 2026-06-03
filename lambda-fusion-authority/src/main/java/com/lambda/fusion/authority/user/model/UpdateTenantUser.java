package com.lambda.fusion.authority.user.model;

import com.lambda.cloud.core.annotation.AutoConverter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@AutoConverter(target = User.class)
@Data
public class UpdateTenantUser {

    @Size(max = 16, message = "用户昵称长度不能超过16个字符")
    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "手机号码")
    private String mobile;

    @Schema(description = "电子邮箱")
    private String email;
}
