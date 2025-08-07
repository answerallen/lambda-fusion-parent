package com.lambda.fusion.authority.role.bean;

import java.util.Set;
import lombok.Data;

@Data
public class AccessPermissionDO {

    private String authority;
    private String tenantId;
    private Set<String> ids;
    private String id;
    private Integer status;
}
