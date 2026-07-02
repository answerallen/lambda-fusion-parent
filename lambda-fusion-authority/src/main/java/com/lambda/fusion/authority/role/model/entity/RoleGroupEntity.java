package com.lambda.fusion.authority.role.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("la_role_groups")
public class RoleGroupEntity {
    @TableId
    private String groupId;

    private String groupName;

    private String tenantId;

    @TableField(exist = false)
    private Boolean noPermission;

    @Override
    public boolean equals(Object group) {
        if (group instanceof RoleGroupEntity) {
            return this.groupId.equals(((RoleGroupEntity) group).getGroupId());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.groupId.hashCode();
    }
}
