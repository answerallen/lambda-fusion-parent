package com.lambda.fusion.authority.role.model;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "角色分组信息")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class GroupRole {
    @Schema(description = "分组ID")
    private String groupId;

    @Schema(description = "组名")
    private String groupName;

    @Schema(description = "角色列表")
    private List<Role> roles;

    @Schema(description = "禁止批被分配")
    private Boolean disableAssignment;

    @Schema(description = "是否可以被操作")
    private Boolean disableOperations;
}
