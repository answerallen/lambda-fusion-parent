package com.lambda.fusion.authority.user.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 个人中心扩展字段
 *
 */
@Data
@TableName("la_user_fields")
@Schema(description = "用户自定义字段信息")
public class UserFieldsEntity {

    @Schema(description = "用户名")
    @TableField("USERNAME")
    private String username;

    @TableField("FIELD_NAME")
    @Schema(description = "字段名")
    private String fieldName;

    @TableField("FIELD_VALUE")
    @Schema(description = "字段值")
    private String fieldValue;

    @TableField("TENANT_ID")
    private String tenantId;
}
