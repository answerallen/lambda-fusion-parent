package com.lambda.fusion.auth.organization.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Schema(description = "用户组织")
@AllArgsConstructor
@NoArgsConstructor
public class UserOrganization {
    @Schema(description = "用户ID")
    public String userId;
    @Schema(description = "组织ID")
    private String organizationId;
    private String tenantid;
}
