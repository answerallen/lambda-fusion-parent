package com.lambda.fusion.auth.role.bean;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "角色信息")
public class MutableRole extends SimpleRole {

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "创建时间")
    private Date createDate;

    @Schema(description = "备注")
    private String remarks;

    @Schema(description = "内置的")
    private boolean builtIn = false;

    private String owner;
    private String tenantId;

    @Schema(description = "角色类型:功能角色为0，数据角色为1", allowableValues = "0,1")
    private int roleType;

    @Schema(description = "数据类型")
    private int dataType;

    @Schema(description = "是否启用:未启用为0，启用为1")
    private int enabled;

    @Schema(description = "分组ID")
    private String groupId;

    @Schema(description = "禁止批量分配人员")
    private Boolean disAllocation;

    @Schema(description = "判断是否有权限")
    private Boolean noPermission;

    @Schema(description = "是否不可用")
    private Boolean inAvailable;

    public MutableRole() {
        super();
    }

    public MutableRole(String authority) {
        super(authority);
    }

    public Date getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }
}
