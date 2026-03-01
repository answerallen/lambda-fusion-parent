package com.lambda.fusion.authority.domain.organization;

import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@AutoConverter(target = UserOrganizationEntity.class)
@Data
@Schema(description = "用户组织")
@AllArgsConstructor
@NoArgsConstructor
public class UserOrganizationChange extends BaseDTO<UserOrganizationEntity> {

    @NotEmpty
    @Schema(description = "用户ID")
    private String username;

    @NotEmpty
    @Schema(description = "组织ID")
    private String organizationId;

    private String tenantId;
}
