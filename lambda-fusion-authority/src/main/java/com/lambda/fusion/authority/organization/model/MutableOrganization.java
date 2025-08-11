package com.lambda.fusion.authority.organization.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MutableOrganization extends Organization {
    private String username;
}
