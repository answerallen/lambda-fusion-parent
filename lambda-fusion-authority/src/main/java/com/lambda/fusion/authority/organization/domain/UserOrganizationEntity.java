package com.lambda.fusion.authority.organization.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@TableName("LA_USER_ORGANIZATION")
@Data
public class UserOrganizationEntity {

    @TableField("USERNAME")
    private String username;

    @TableField("ORGANIZATION_ID")
    private String organizationId;

    @TableField("TENANT_ID")
    private String tenantId;
}
