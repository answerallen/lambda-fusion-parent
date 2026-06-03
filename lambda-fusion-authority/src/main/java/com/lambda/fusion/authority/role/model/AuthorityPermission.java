package com.lambda.fusion.authority.role.model;

import java.util.Set;
import lombok.Data;

@Data
public class AuthorityPermission {
    private String authority;
    private String tenantId;
    private Set<String> ids;
    private String id;
    private Integer status;
}
