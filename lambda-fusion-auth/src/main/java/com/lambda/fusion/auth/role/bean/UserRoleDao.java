package com.lambda.fusion.auth.role.bean;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("m1_user_roles")
public class UserRoleDao {
    /**
     * 用户ID
     */
    private String userid;
    /**
     * 角色
     */
    private String authority;
    /**
     * 租户
     */
    private String tenantid;
}
