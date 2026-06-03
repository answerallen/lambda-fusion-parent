package com.lambda.fusion.authority.user.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("la_user_roles")
public class UserRoleEntity {
    @TableField("USERNAME")
    private String username;

    @TableField("AUTHORITY")
    private String authority;

    @TableField("TENANT_ID")
    private String tenantId;
}
