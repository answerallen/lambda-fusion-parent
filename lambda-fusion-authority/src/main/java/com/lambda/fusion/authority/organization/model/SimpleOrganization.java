package com.lambda.fusion.authority.organization.model;

import com.fasterxml.jackson.annotation.JsonRootName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonRootName("Organization")
public class SimpleOrganization {
    String id;

    String alias;

    String fullName;

    public SimpleOrganization() {}

    public SimpleOrganization(String id) {
        this.id = id;
    }

    public SimpleOrganization(String id, String alias) {
        this.id = id;
        this.alias = alias;
    }
}
