package com.lambda.fusion.authority.organization.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@TableName("la_user_organization")
@Data
public class UserOrganizationEntity {

    @TableField("USERNAME")
    private String userid;

    @TableField("ORGANIZATION_ID")
    private String organizationId;

    @TableField("TENANT_ID")
    private String tenantId;
}
