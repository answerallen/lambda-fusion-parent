package com.lambda.fusion.auth.role.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@TableName("LA_GROUPS")

public class GroupDao {
    @TableId
    private String groupId;
    private String groupName;
    private String tenantid;
    @TableField(exist = false)
    private Boolean noPermission;

    @Override
    public boolean equals(Object group) {
        if (group instanceof GroupDao) {
            return this.groupId.equals(((GroupDao) group).getGroupId());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.groupId.hashCode();
    }
}
