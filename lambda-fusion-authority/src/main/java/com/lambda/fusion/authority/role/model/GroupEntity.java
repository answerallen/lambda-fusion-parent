package com.lambda.fusion.authority.role.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("la_role_groups")
public class GroupEntity {
    @TableId
    private String groupId;

    private String groupName;
    private String tenantId;

    @TableField(exist = false)
    private Boolean noPermission;

    @Override
    public boolean equals(Object group) {
        if (group instanceof GroupEntity) {
            return this.groupId.equals(((GroupEntity) group).getGroupId());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.groupId.hashCode();
    }
}
