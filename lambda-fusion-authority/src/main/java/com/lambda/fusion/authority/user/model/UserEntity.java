package com.lambda.fusion.authority.user.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

@Data
@TableName("la_users")
public class UserEntity {

    @TableId(value = "USERNAME")
    private String username;

    @TableField("PASSWORD")
    private String password;

    @TableField("NICKNAME")
    private String nickname;

    @TableField("MOBILE")
    private String mobile;

    @TableField("EMAIL")
    private String email;

    @TableField("ENABLED")
    private Integer enabled;

    @TableField("TENANT_ID")
    private String tenantId;

    @TableField("CREATOR")
    private String creator;

    @TableField("CREATE_DATE")
    private Date createDate;

    @TableField("EXPIRED_TIME")
    private Date expiredTime;
}
