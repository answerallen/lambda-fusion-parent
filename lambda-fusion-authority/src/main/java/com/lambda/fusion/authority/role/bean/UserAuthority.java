package com.lambda.fusion.authority.role.bean;

import lombok.Data;

@Data
public class UserAuthority {
    /**
     * 角色编号
     */
    private String authority;
    /**
     * 组织编号
     */
    private String orgid;
}
