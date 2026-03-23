package com.lambda.fusion.datascope.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 通用数据权限映射表
 */
@Data
@TableName("la_datascopes")
public class DataScopeEntity {

    /**
     * 业务数据ID(例如：部门ID、项目ID、合同ID)
     */
    @TableField("ID")
    private String id;

    /**
     * 授权主体ID(例如：角色ID、用户ID)
     */
    @TableField("TID")
    private String tid;
    
    /**
     * 主体类型: USER, ROLE, ORG, GROUP, CLIENT
     */
    @TableField("TARGET_TYPE")
    private String targetType;

    /**
     * 业务数据类型：0-部门数据 1-项目数据 2-XX业务数据
     */
    @TableField("DOMAIN_TYPE")
    private Integer domainType;

    /**
     * 层级(预留)
     */
    @TableField("RANK_LEVEL")
    private Integer rankLevel;

    /**
     * 前端勾选状态(1全选 2半选)
     */
    @TableField("CHECKED")
    private Integer checked;

    /**
     * 多租户隔离字段
     */
    @TableField("TENANT_ID")
    private String tenantId;
}
