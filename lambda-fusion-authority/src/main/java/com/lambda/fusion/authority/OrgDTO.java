package com.lambda.fusion.authority;

import lombok.Data;

@Data
public class OrgDTO {
    String id;

    String alias;

    String fullName;

    public OrgDTO() {}

    public OrgDTO(String id) {
        this.id = id;
    }

    public OrgDTO(String id, String alias) {
        this.id = id;
        this.alias = alias;
    }
}
