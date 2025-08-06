package com.lambda.fusion.auth.role.bean;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;


@Data
@Schema(description = "角色分组信息")

public class GroupRoleVo {
    @Schema(description = "分组ID")
    private String groupId;
    @Schema(description = "组名")
    private String groupName;
    @Schema(description = "角色列表")
    private List<MutableRole> roles;
    @Schema(description = "是否拥有操作权限")
    private Boolean noPermission;
    @Schema(description = "是否不可用")
    private Boolean inAvailable;

}
