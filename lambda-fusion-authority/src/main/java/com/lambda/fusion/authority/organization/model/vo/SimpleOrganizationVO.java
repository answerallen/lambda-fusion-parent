package com.lambda.fusion.authority.organization.model.vo;

import com.fasterxml.jackson.annotation.JsonRootName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonRootName("Organization")
public class SimpleOrganizationVO {
    String id;

    String alias;

    String fullName;

    public SimpleOrganizationVO() {}

    public SimpleOrganizationVO(String id) {
        this.id = id;
    }

    public SimpleOrganizationVO(String id, String alias) {
        this.id = id;
        this.alias = alias;
    }
}
