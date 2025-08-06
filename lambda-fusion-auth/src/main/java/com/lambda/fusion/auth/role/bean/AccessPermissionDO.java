package com.lambda.fusion.auth.role.bean;


import lombok.Data;

import java.util.Set;

@Data
public class AccessPermissionDO {

    private String authority;
    private String tenantid;
    private Set<String> ids;
    private String id;
    private Integer status;
}
