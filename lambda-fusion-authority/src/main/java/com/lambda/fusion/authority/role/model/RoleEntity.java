package com.lambda.fusion.authority.role.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("la_roles") // 指定表名
public class RoleEntity {

    @TableId(value = "AUTHORITY", type = IdType.ASSIGN_ID)
    private String authority;

    @TableField("ALIAS")
    private String alias;

    @TableField("ICON")
    private String icon;

    @TableField("REMARKS")
    private String remarks;

    @TableField("TENANT_ID")
    private String tenantId;

    @TableField("HIDDEN")
    private Integer hidden;

    @TableField("ENABLED")
    private Integer enabled;

    @TableField("DATA_TYPE")
    private Integer dataType;

    @TableField("ROLE_TYPE")
    private Integer roleType;

    @TableField("GROUP_ID")
    private String groupId;

    @TableField("CREATE_BY")
    private String createBy;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
