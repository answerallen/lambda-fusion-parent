package com.lambda.fusion.authority.organization.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
public class MutableOrganization extends Organization {
    private String username;
}
