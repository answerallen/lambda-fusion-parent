package com.lambda.fusion.auth.organization.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@EqualsAndHashCode(callSuper = false)

public class MutableOrganization extends Organization {
    private String username;
}


