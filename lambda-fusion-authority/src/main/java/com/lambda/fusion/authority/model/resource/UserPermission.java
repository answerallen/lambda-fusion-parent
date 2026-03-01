package com.lambda.fusion.authority.model.resource;

import lombok.Data;

@Data
public class UserPermission {

    /**
     * 用户名
     */
    private String username;

    /**
     * 接口权限id
     */
    private String permissionId;
}
