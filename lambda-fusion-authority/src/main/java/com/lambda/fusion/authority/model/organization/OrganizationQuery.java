package com.lambda.fusion.authority.model.organization;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import lombok.Data;

@Data
@SuppressFBWarnings("EI_EXPOSE_REP")
public class OrganizationQuery {

    boolean enabled;

    Integer mode;

    String owner;

    String parentKeys;

    String name;

    String alias;

    List<String> ids;

    String tenantId;
}
