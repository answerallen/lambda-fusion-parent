package com.lambda.fusion.auth.role.bean;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "批量添加角色用户")
public class BatchAddRoleUser {
    @Schema(description = "角色", required = true)
    private String roleId;

    @Schema(description = "用户账号", required = true)
    private List<String> username;
}
