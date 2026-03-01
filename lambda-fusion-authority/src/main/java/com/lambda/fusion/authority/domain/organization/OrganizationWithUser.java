package com.lambda.fusion.authority.domain.organization;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrganizationWithUser extends Organization {
    private String username;
}
