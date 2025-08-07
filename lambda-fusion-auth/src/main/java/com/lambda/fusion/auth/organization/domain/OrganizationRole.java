package com.lambda.fusion.auth.organization.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Schema(description = "组织角色")
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationRole {
    @Schema(description = "角色ID")
    private String authority;

    @Schema(description = "组织ID")
    private String organizationId;
}
