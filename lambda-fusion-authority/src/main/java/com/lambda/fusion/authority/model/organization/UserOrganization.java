package com.lambda.fusion.authority.model.organization;

import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseVO;
import io.swagger.v3.oas.annotations.media.Schema;
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
public class UserOrganization extends BaseVO<UserOrganizationEntity> {

    @Schema(description = "用户ID")
    private String userId;

    @Schema(description = "组织ID")
    private String organizationId;

    private String tenantId;
}
