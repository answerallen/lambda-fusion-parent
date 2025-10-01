package com.lambda.fusion.authority.user.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("la_user_roles")
public class UserRole {

    @TableId(value = "userid")
    private String userid;

    @TableId(value = "AUTHORITY")
    private String authority;

    @TableField("TENANT_ID")
    private String tenantId;
}