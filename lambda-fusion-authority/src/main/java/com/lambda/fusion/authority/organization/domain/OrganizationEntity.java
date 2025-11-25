package com.lambda.fusion.authority.organization.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.util.Date;
import lombok.Data;

@Data
@TableName("la_organization") // 表名
public class OrganizationEntity {

    @TableId(value = "ID", type = IdType.ASSIGN_UUID)
    private String id; // 主键

    @TableField("ORG_OWNER")
    private String owner; // 组织拥有者

    @TableField("ORG_CATEGORY")
    private Integer category; // 组织类别

    @TableField("ORG_RANK")
    private Integer rank; // 组织级别

    @TableField("ORG_TYPE")
    private Integer type; // 组织类型

    @TableField("ORG_NAME")
    private String name; // 组织名称

    @TableField("ALIAS")
    private String alias; // 别名

    @TableField("PARENT_ID")
    private String parentId; // 父ID，NULL 代表顶级

    @TableField("CREATE_DATE")
    private Date createDate; // 创建日期，默认为当前时间

    @TableField("REMARKS")
    private String remarks; // 备注

    @TableField("PARENT_KEYS")
    private String parentKeys; // 父节点关键字

    @TableField(value = "ENABLED")
    private Integer enabled; // 是否启用，未启用:0,已启用:1，默认值1

    @TableField("TENANT_ID")
    private String tenantId; // 租户

    @TableField(value = "ORDER_NO")
    private Integer orderNo; // 组织排序号，默认值1
}
