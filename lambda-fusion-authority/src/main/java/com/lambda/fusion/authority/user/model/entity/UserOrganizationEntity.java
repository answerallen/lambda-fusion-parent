package com.lambda.fusion.authority.user.model.entity;


import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@TableName("la_user_organization")
@Data
public class UserOrganizationEntity {

    private String userid;

    private String organizationId;

    private String tenantId;
}