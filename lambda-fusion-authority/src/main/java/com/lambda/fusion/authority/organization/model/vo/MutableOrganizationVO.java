package com.lambda.fusion.authority.organization.model.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MutableOrganizationVO extends OrganizationVO {
    private String username;
}
