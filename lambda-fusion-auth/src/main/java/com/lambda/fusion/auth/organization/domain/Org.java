package com.lambda.fusion.auth.organization.domain;

import com.fasterxml.jackson.annotation.JsonRootName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonRootName("Organization")
public class Org {
    String id;

    String alias;

    String fullName;

    public Org() {}

    public Org(String id) {
        this.id = id;
    }

    public Org(String id, String alias) {
        this.id = id;
        this.alias = alias;
    }
}
