package com.lambda.fusion.authority.organization.domain;

import com.fasterxml.jackson.annotation.JsonRootName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonRootName("Organization")
public class OrganizationSummary {
    String id;

    String alias;

    String fullName;

    public OrganizationSummary() {}

    public OrganizationSummary(String id) {
        this.id = id;
    }

    public OrganizationSummary(String id, String alias) {
        this.id = id;
        this.alias = alias;
    }
}
