package com.lambda.fusion.auth.organization.domain;

import java.util.List;
import lombok.Data;

@Data
public class Parameters {

    boolean enabled;

    Integer mode;

    String owner;

    String parentkeys;

    String name;

    String alias;

    List<String> ids;

    String tenantId;
}
