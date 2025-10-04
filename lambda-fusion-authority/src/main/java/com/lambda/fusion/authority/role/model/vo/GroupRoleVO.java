package com.lambda.fusion.authority.role.model.vo;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "角色分组信息")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class GroupRoleVO {
    @Schema(description = "分组ID")
    private String groupId;

    @Schema(description = "组名")
    private String groupName;

    @Schema(description = "角色列表")
    private List<MutableRoleVO> roles;

    @Schema(description = "是否拥有操作权限")
    private Boolean noPermission;

    @Schema(description = "是否不可用")
    private Boolean inAvailable;
}
