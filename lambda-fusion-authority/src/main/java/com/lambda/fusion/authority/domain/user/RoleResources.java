package com.lambda.fusion.authority.domain.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Schema(description = "用户权限信息")
public class RoleResources {

    private String resourceId;

    private String laManage;
}
