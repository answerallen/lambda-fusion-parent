package com.lambda.fusion.auth.role.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("LA_GROUPS")
public class Group {
    @TableId
    private String groupId;

    private String groupName;
    private String tenantId;

    @TableField(exist = false)
    private Boolean noPermission;

    @Override
    public boolean equals(Object group) {
        if (group instanceof Group) {
            return this.groupId.equals(((Group) group).getGroupId());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.groupId.hashCode();
    }
}
