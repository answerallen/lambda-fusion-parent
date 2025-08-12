package com.lambda.fusion.authority.organization.model;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import lombok.Data;

@Data
@SuppressFBWarnings("EI_EXPOSE_REP")
public class Parameters {

    boolean enabled;

    Integer mode;

    String owner;

    String parentKeys;

    String name;

    String alias;

    List<String> ids;

    String tenantId;
}
