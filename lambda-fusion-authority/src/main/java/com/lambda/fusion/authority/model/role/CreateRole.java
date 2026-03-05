package com.lambda.fusion.authority.model.role;

import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseDTO;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@AutoConverter(target = RoleEntity.class)
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "角色信息")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class CreateRole extends BaseDTO<RoleEntity> {

    @Schema(description = "角色名")
    private String authority;

    @Schema(description = "别名")
    private String alias;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "备注")
    private String remarks;

    @Schema(description = "租户")
    private String tenantId;

    @Schema(description = "角色类型:功能角色为0，数据角色为1", allowableValues = "0,1")
    private int roleType;

    @Schema(description = "数据类型")
    private int dataType;

    @Schema(description = "是否启用:未启用为0，启用为1")
    private int enabled;

    @Schema(description = "分组ID")
    private String groupId;

    @Schema(description = "拥有者")
    private String owner;

}
