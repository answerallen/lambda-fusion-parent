package com.lambda.fusion.authority.role.model;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "批量添加角色用户")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class BatchAddRoleUser {
    @Schema(description = "角色")
    private String roleId;

    @Schema(description = "用户账号")
    private List<String> username;
}
