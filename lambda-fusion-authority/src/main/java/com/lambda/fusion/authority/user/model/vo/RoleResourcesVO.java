package com.lambda.fusion.authority.user.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Schema(description = "用户权限信息")
public class RoleResourcesVO {

    private String resourceId;

    private String laManage;
}
